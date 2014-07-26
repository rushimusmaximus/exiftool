package com.thebuzzmedia.exiftool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages an external exif process in keep alive mode.
 */
public class KeepAliveExifProxy implements ExifProxy {
	private final List<String> startupArgs;
	private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
	private final Timer cleanupTimer = new Timer(ExifTool.CLEANUP_THREAD_NAME,
			true);
	private long inactivityTimeout = 0;
	private volatile long lastRunStart = 0;
	private volatile ExifProcess process;

	public KeepAliveExifProxy(String exifCmd, List<String> baseArgs) {
		inactivityTimeout = Long.getLong(
				ExifTool.ENV_EXIF_TOOL_PROCESSCLEANUPDELAY,
				ExifTool.DEFAULT_PROCESS_CLEANUP_DELAY);
		startupArgs = new ArrayList<String>(baseArgs.size() + 5);
		startupArgs.add(exifCmd);
		startupArgs.addAll(Arrays.asList("-stay_open", "True"));
		startupArgs.addAll(baseArgs);
		startupArgs.addAll(Arrays.asList("-@", "-"));
		// runs every minute to check if process has been inactive too long
		cleanupTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (process != null && lastRunStart > 0
						&& inactivityTimeout > 0) {
					if ((System.currentTimeMillis() - lastRunStart) > inactivityTimeout) {
						synchronized (this) {
							if (process != null) {
								process.close();
							}
						}
					}
				}
			}
		}, 60 * 1000); // every minute
	}

	public void setInactiveTimeout(long mills) {
		this.inactivityTimeout = mills;
	}

	@Override
	public void startup() {
		shuttingDown.set(false);
		if (process == null || process.isClosed()) {
			synchronized (this) {
				if (process == null || process.isClosed()) {
					ExifTool.log
							.debug("Starting daemon ExifTool process and creating read/write streams (this only happens once)...");
					process = new ExifProcess(true, startupArgs);
				}
			}
		}
	}

	@Override
	public Map<String, String> execute(final long runTimeoutMills,
			List<String> args) throws IOException {
		lastRunStart = System.currentTimeMillis();
		int attempts = 0;
		while (attempts < 3 && !shuttingDown.get()) {
			attempts++;
			if (process == null || process.isClosed()) {
				synchronized (this) {
					if (process == null || process.isClosed()) {
						ExifTool.log
								.debug("Starting daemon ExifTool process and creating read/write streams (this only happens once)...");
						process = new ExifProcess(true, startupArgs);
					}
				}
			}
			TimerTask attemptTimer = null;
			try {
				if (runTimeoutMills > 0) {
					attemptTimer = new TimerTask() {
						@Override
						public void run() {
							if (process != null && !process.isClosed()) {
								ExifTool.log
										.warn("Process ran too long closing, max "
												+ runTimeoutMills + " mills");
								process.close();
							}
						}
					};
					cleanupTimer.schedule(attemptTimer, runTimeoutMills);
				}
				ExifTool.log
						.debug("Streaming arguments to ExifTool process...");
				return process.sendToRunning(args);
			} catch (IOException ex) {
				if (ExifTool.STREAM_CLOSED_MESSAGE.equals(ex.getMessage())
						&& !shuttingDown.get()) {
					// only catch "Stream Closed" error (happens when process
					// has died)
					ExifTool.log.warn(String.format(
							"Caught IOException(\"%s\"), will restart daemon",
							ExifTool.STREAM_CLOSED_MESSAGE));
					process.close();
				} else {
					throw ex;
				}
			} finally {
				if (attemptTimer != null)
					attemptTimer.cancel();
			}
		}
		if (shuttingDown.get()) {
			throw new IOException("Shutting Down");
		}
		throw new IOException("Ran out of attempts");
	}

	@Override
	public boolean isRunning() {
		return process != null && !process.isClosed();
	}

	@Override
	public void shutdown() {
		shuttingDown.set(true);
	}
}