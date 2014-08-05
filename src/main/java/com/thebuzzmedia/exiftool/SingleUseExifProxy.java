package com.thebuzzmedia.exiftool;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class SingleUseExifProxy implements ExifProxy {
	private final Timer cleanupTimer = new Timer(ExifTool.CLEANUP_THREAD_NAME,
			true);
	private final List<String> baseArgs;
	private final Charset charset;

	public SingleUseExifProxy(String exifCmd, List<String> defaultArgs, Charset charset) {
		this.baseArgs = new ArrayList<String>(defaultArgs.size() + 1);
		this.baseArgs.add(exifCmd);
		this.baseArgs.addAll(defaultArgs);
		this.charset = charset;
	}

	@Override
	public Map<String, String> execute(final long runTimeoutMills,
			List<String> args) throws IOException {
		List<String> newArgs = new ArrayList<String>(baseArgs.size()
				+ args.size());
		newArgs.addAll(baseArgs);
		newArgs.addAll(args);
		final ExifProcess process = new ExifProcess(false, newArgs,charset);
		TimerTask attemptTimer = null;
		if (runTimeoutMills > 0) {
			attemptTimer = new TimerTask() {
				@Override
				public void run() {
					if (!process.isClosed()) {
						ExifTool.log.warn("Process ran too long closing, max "
								+ runTimeoutMills + " mills");
						process.close();
					}
				}
			};
			cleanupTimer.schedule(attemptTimer, runTimeoutMills);
		}
		try {
			return process.readResponse();
		} finally {
			process.close();
			if (attemptTimer != null)
				attemptTimer.cancel();
		}
	}

	@Override
	public void startup() {
		;
	}

	@Override
	public boolean isRunning() {
		return false;
	}

	@Override
	public void shutdown() {
		;
	}
}