package com.thebuzzmedia.exiftool;

// ================================================================================
/**
 * All the read options, is immutable, copy on change, fluent style "with"
 * setters.
 */
public class ReadOptions {
	final long runTimeoutMills;
	final boolean convertTypes;
	final boolean numericOutput;
	final boolean showDuplicates;
	final boolean showEmptyTags;

	public ReadOptions() {
		this(0, false, false, false, false);
	}

	private ReadOptions(long runTimeoutMills, boolean convertTypes,
			boolean numericOutput, boolean showDuplicates, boolean showEmptyTags) {
		this.runTimeoutMills = runTimeoutMills;
		this.convertTypes = convertTypes;
		this.numericOutput = numericOutput;
		this.showDuplicates = showDuplicates;
		this.showEmptyTags = showEmptyTags;
	}

	@Override
	public String toString() {
		return String
				.format("%s(runTimeout:%,d convertTypes:%s showDuplicates:%s showEmptyTags:%s)",
						getClass().getSimpleName(), runTimeoutMills,
						convertTypes, showDuplicates, showEmptyTags);
	}

	/**
	 * Sets the maximum time a process can run
	 */
	public ReadOptions withRunTimeoutMills(long mills) {
		return new ReadOptions(mills, convertTypes, numericOutput,
				showDuplicates, showEmptyTags);
	}

	/**
	 * By default all values will be returned as the strings printed by the
	 * exiftool. If this is enabled then {@link MetadataTag#getType()} is used
	 * to cast the string into a java type.
	 */
	public ReadOptions withConvertTypes(boolean enabled) {
		return new ReadOptions(runTimeoutMills, enabled, numericOutput,
				showDuplicates, showEmptyTags);
	}

	/**
	 * Setting this to true will add the "-n" option causing the ExifTool to
	 * output of some tags to change.
	 * <p/>
	 * ExifTool, via the <code>-n</code> command line arg, is capable of
	 * returning most values in their raw numeric form (e.g.
	 * Aperture="2.8010323841") as well as a more human-readable/friendly format
	 * (e.g. Aperture="2.8").
	 * <p/>
	 * While the {@link Tag}s defined on this class do provide a hint at the
	 * type of the result (see {@link MetadataTag#getType()}), that hint only
	 * applies when the numeric value is returned.
	 * <p/>
	 * If the caller finds the human-readable format easier to process, Set this
	 * to false, the default.
	 * <p/>
	 * In order to see the types of values that are returned when human readable
	 * is used (default), you can check the comprehensive <a href=
	 * "http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html">
	 * ExifTool Tag Guide</a>.
	 * <p/>
	 * This makes sense with some values like Aperture that in numeric format
	 * end up returning as 14-decimal-place, high precision values that are near
	 * the intended value (e.g. "2.79999992203711" instead of just returning
	 * "2.8"). On the other hand, other values (like Orientation) are easier to
	 * parse when their numeric value (1-8) is returned instead of a much longer
	 * friendly name (e.g. "Mirror horizontal and rotate 270 CW").
	 * 
	 * Attempted from work done by
	 * 
	 * @author Riyad Kalla (software@thebuzzmedia.com)
	 */
	public ReadOptions withNumericOutput(boolean enabled) {
		return new ReadOptions(runTimeoutMills, convertTypes, enabled,
				showDuplicates, showEmptyTags);
	}

	/**
	 * If enabled will show tags which are duplicated between different tag
	 * regions, relates to the "-a" option in ExifTool.
	 */
	public ReadOptions withShowDuplicates(boolean enabled) {
		return new ReadOptions(runTimeoutMills, convertTypes, numericOutput,
				enabled, showEmptyTags);
	}

	public ReadOptions withShowEmptyTags(boolean enabled) {
		return new ReadOptions(runTimeoutMills, convertTypes, numericOutput,
				showDuplicates, enabled);
	}
}