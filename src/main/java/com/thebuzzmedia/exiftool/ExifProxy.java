package com.thebuzzmedia.exiftool;

import java.util.*;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;

/**
 * A Proxy to an Exif Process, will restart if backing exif process died, or run new one on every call.
 * 
 * @author Matt Gile, msgile
 */
public interface ExifProxy {
	public void startup();

	public List<String> execute(long runTimeoutMills, List<String> args);

	public boolean isRunning();

	public void shutdown();

	public static class $ {

	}
}