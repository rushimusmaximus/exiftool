package com.thebuzzmedia.exiftool;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A Proxy to an Exif Process, will restart if backing exif process died, or run
 * new one on every call.
 * 
 * @author Matt Gile, msgile
 */
public interface ExifProxy {
	public void startup();

	public Map<String, String> execute(long runTimeoutMills, List<String> args)
			throws IOException;

	public boolean isRunning();

	public void shutdown();
}