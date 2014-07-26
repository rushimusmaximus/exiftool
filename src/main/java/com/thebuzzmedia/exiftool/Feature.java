package com.thebuzzmedia.exiftool;

// ================================================================================
/**
 * Enum used to define the different kinds of features in the native
 * ExifTool executable that this class can help you take advantage of.
 * <p/>
 * These flags are different from {@link Tag}s in that a "feature" is
 * determined to be a special functionality of the underlying ExifTool
 * executable that requires a different code-path in this class to take
 * advantage of; for example, <code>-stay_open True</code> support.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
public enum Feature {
	/**
	 * Enum used to specify that you wish to launch the underlying ExifTool
	 * process with <code>-stay_open True</code> support turned on that this
	 * class can then take advantage of.
	 * <p/>
	 * Required ExifTool version is <code>8.36</code> or higher.
	 */
	STAY_OPEN(8, 36);

	private VersionNumber requireVersion;

	private Feature(int... numbers) {
		this.requireVersion = new VersionNumber(numbers);
	}

	/**
	 * Used to get the version of ExifTool required by this feature in order
	 * to work.
	 * 
	 * @return the version of ExifTool required by this feature in order to
	 *         work.
	 */
	VersionNumber getVersion() {
		return requireVersion;
	}

	boolean isSupported(VersionNumber exifVersionNumber) {
		return requireVersion.isBeforeOrEqualTo(exifVersionNumber);
	}
}