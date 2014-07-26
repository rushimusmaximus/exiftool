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
	STAY_OPEN(8, 36),
	/**
	 * Acitves the MWG modules. The Metadata Working Group (MWG) recommends
	 * techniques to allow certain overlapping EXIF, IPTC and XMP tags to be
	 * reconciled when reading, and synchronized when writing. The MWG Composite
	 * tags below are designed to aid in the implementation of these
	 * recommendations. Will add the args " -use MWG"
	 * 
	 * @see <a
	 *      href="http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/MWG.html">ExifTool
	 *      MWG Docs</a> !! Note these version numbers are not correct
	 */
	MWG_MODULE(8, 36), ;

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