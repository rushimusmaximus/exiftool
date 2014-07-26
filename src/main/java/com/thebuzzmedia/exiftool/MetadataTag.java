package com.thebuzzmedia.exiftool;

// ================================================================================
/**
 * Base type for all "tag" passed to exiftool. The key is the value passed to
 * the exiftool like "-creator". The Types is used for automatic type
 * conversions.
 * 
 */
public interface MetadataTag {
	/**
	 * Returns the values passed to exiftool
	 */
	public String getKey();

	/**
	 * The types
	 */
	public Class getType();

	public boolean isMapped();
}