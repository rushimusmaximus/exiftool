package com.thebuzzmedia.exiftool;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages an external exif process in keep alive mode.
 */
public class KeepAliveExifProxy implements ExifProxy {
	private final List<String> startupArgs;
	private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
	private final Timer cleanupTimer = new Timer(ExifToolNew3.CLEANUP_THREAD_NAME, true);
	private final long inactivityTimeout;
	private volatile long lastRunStart = 0;
	private volatile ExifProcess process;
	private final Charset charset;
	public KeepAliveExifProxy(String exifCmd, List<String> baseArgs, long inactivityTimeoutParam) {
		this(exifCmd,baseArgs,inactivityTimeoutParam,ExifToolNew3.computeDefaultCharset(EnumSet.noneOf(Feature.class)));
	}
	public KeepAliveExifProxy(String exifCmd, List<String> baseArgs, Charset charset) {
		this(exifCmd, baseArgs, Long.getLong(ExifToolNew.ENV_EXIF_TOOL_PROCESSCLEANUPDELAY,
				ExifToolNew.DEFAULT_PROCESS_CLEANUP_DELAY), charset);
	}

	public KeepAliveExifProxy(String exifCmd, List<String> baseArgs, long inactivityTimeoutParam, Charset charset) {
		this.inactivityTimeout = inactivityTimeoutParam;
		startupArgs = new ArrayList<String>(baseArgs.size() + 5);
		startupArgs.add(exifCmd);
		startupArgs.addAll(Arrays.asList("-stay_open", "True"));
		startupArgs.addAll(baseArgs);
		startupArgs.addAll(Arrays.asList("-@", "-"));
		this.charset = charset;
		// runs every minute to check if process has been inactive too long
		if (inactivityTimeout != 0) {
			cleanupTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					if (process != null && lastRunStart > 0 && inactivityTimeout > 0) {
						if ((System.currentTimeMillis() - lastRunStart) > inactivityTimeout) {
							synchronized (this) {
								if (process != null) {
									process.close();
								}
							}
						}
					} else if (lastRunStart == 0) {
						shutdown();
					}
				}
			}, inactivityTimeout
			// 60 * 1000// every minute
					);
		}
	}

	@Override
	public void startup() {
		shuttingDown.set(false);
		if (process == null || process.isClosed()) {
			synchronized (this) {
				if (process == null || process.isClosed()) {
					ExifToolNew3.log
							.debug("Starting daemon ExifToolNew3 process and creating read/write streams (this only happens once)...");
					process = new ExifProcess(true, startupArgs, charset);
				}
			}
		}
	}

	@Override
	public List<String> execute(final long runTimeoutMills, List<String> args) {
		lastRunStart = System.currentTimeMillis();
		int attempts = 0;
		while (attempts < 3 && !shuttingDown.get()) {
			attempts++;
			if (process == null || process.isClosed()) {
				synchronized (this) {
					if (process == null || process.isClosed()) {
						ExifToolNew3.log
								.debug("Starting daemon ExifToolNew3 process and creating read/write streams (this only happens once)...");
						process = new ExifProcess(true, startupArgs, charset);
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
								ExifToolNew3.log
										.warn("Process ran too long closing, max " + runTimeoutMills + " mills");
								process.close();
							}
						}
					};
					cleanupTimer.schedule(attemptTimer, runTimeoutMills);
				}
				ExifToolNew3.log.debug("Streaming arguments to ExifToolNew3 process...");
				return process.sendToRunning(args);
			} catch (IOException ex) {
				if (ExifToolNew3.STREAM_CLOSED_MESSAGE.equals(ex.getMessage()) && !shuttingDown.get()) {
					// only catch "Stream Closed" error (happens when
					// process has died)
					ExifToolNew3.log.warn(String.format("Caught IOException(\"%s\"), will restart daemon",
							ExifToolNew3.STREAM_CLOSED_MESSAGE));
					process.close();
				} else {
					throw new RuntimeException(ex);
				}
			} finally {
				if (attemptTimer != null)
					attemptTimer.cancel();
			}
		}
		if (shuttingDown.get()) {
			throw new RuntimeException("Shutting Down");
		}
		throw new RuntimeException("Ran out of attempts");
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