package com.thebuzzmedia.exiftool;

import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

// ================================================================================
/**
 * Enum used to pre-define a convenient list of tags that can be easily
 * extracted from images using this class with an external install of ExifTool.
 * <p/>
 * Each tag defined also includes a type hint for the parsed value associated
 * with it when the default humanReadable is false.
 * <p/>
 * The types provided by each tag are merely a hint based on the <a
 * href="http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html"
 * >ExifTool Tag Guide</a> by Phil Harvey; the caller is free to parse or
 * process the returned {@link String} values any way they wish.
 * <h3>Tag Support</h3>
 * ExifTool is capable of parsing almost every tag known to man (1000+), but
 * this class makes an attempt at pre-defining a convenient list of the most
 * common tags for use.
 * <p/>
 * This list was determined by looking at the common metadata tag values written
 * to images by popular mobile devices (iPhone, Android) as well as cameras like
 * simple point and shoots as well as DSLRs. As an additional source of input
 * the list of supported/common EXIF formats that Flickr supports was also
 * reviewed to ensure the most common/useful tags were being covered here.
 * <p/>
 * Please email me or <a
 * href="https://github.com/thebuzzmedia/imgscalr/issues">file an issue</a> if
 * you think this list is missing a commonly used tag that should be added to
 * it.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
public enum Tag implements MetadataTag {
	// single entry tags
	APERTURE("ApertureValue", Double.class),
	AUTHOR("XPAuthor", String.class),
	COLOR_SPACE("ColorSpace", Integer.class),
	COMMENT("XPComment", String.class),
	CONTRAST("Contrast", Integer.class),
	CREATE_DATE("CreateDate", Date.class),
	CREATION_DATE("CreationDate", Date.class),
	DATE_CREATED("DateCreated", Date.class),
	DATE_TIME_ORIGINAL("DateTimeOriginal", Date.class),
	DIGITAL_ZOOM_RATIO("DigitalZoomRatio", Double.class),
	EXIF_VERSION("ExifVersion", String.class),
	EXPOSURE_COMPENSATION("ExposureCompensation", Double.class),
	EXPOSURE_PROGRAM("ExposureProgram", Integer.class),
	EXPOSURE_TIME("ExposureTime", Double.class),
	FLASH("Flash", Integer.class),
	FOCAL_LENGTH("FocalLength", Double.class),
	FOCAL_LENGTH_35MM("FocalLengthIn35mmFormat", Integer.class),
	FNUMBER("FNumber", String.class),
	GPS_ALTITUDE("GPSAltitude", Double.class),
	GPS_ALTITUDE_REF("GPSAltitudeRef", Integer.class),
	GPS_BEARING("GPSDestBearing", Double.class),
	GPS_BEARING_REF("GPSDestBearingRef", String.class),
	GPS_DATESTAMP("GPSDateStamp", String.class),
	GPS_LATITUDE("GPSLatitude", Double.class),
	GPS_LATITUDE_REF("GPSLatitudeRef", String.class),
	GPS_LONGITUDE("GPSLongitude", Double.class),
	GPS_LONGITUDE_REF("GPSLongitudeRef", String.class),
	GPS_PROCESS_METHOD("GPSProcessingMethod", String.class),
	GPS_SPEED("GPSSpeed", Double.class),
	GPS_SPEED_REF("GPSSpeedRef", String.class),
	GPS_TIMESTAMP("GPSTimeStamp", String.class),
	IMAGE_HEIGHT("ImageHeight", Integer.class),
	IMAGE_WIDTH("ImageWidth", Integer.class),
	ISO("ISO", Integer.class),
	KEYWORDS("XPKeywords", String.class),
	LENS_MAKE("LensMake", String.class),
	LENS_MODEL("LensModel", String.class),
	MAKE("Make", String.class),
	METERING_MODE("MeteringMode", Integer.class),
	MODEL("Model", String.class),
	ORIENTATION("Orientation", Integer.class),
	OWNER_NAME("OwnerName", String.class),
	RATING("Rating", Integer.class),
	RATING_PERCENT("RatingPercent", Integer.class),
	ROTATION("Rotation", Integer.class),
	SATURATION("Saturation", Integer.class),
	SENSING_METHOD("SensingMethod", Integer.class),
	SHARPNESS("Sharpness", Integer.class),
	SHUTTER_SPEED("ShutterSpeedValue", Double.class),
	SOFTWARE("Software", String.class),
	SUBJECT("XPSubject", String.class),
	TITLE("XPTitle", String.class),
	WHITE_BALANCE("WhiteBalance", Integer.class),
	X_RESOLUTION("XResolution", Double.class),
	Y_RESOLUTION("YResolution", Double.class), ;

	/**
	 * Used to get the {@link Tag} identified by the given, case-sensitive, tag
	 * name.
	 * 
	 * @param name
	 *            The case-sensitive name of the tag that will be searched for.
	 * 
	 * @return the {@link Tag} identified by the given, case-sensitive, tag name
	 *         or <code>null</code> if one couldn't be found.
	 */
	public static Tag forName(String name) {
		for (Tag tag : Tag.values()) {
			if (tag.getKey().equals(name)) {
				return tag;
			}
		}
		return null;
	}

	public static Map<Tag, String> toTagMap(Map<String, String> values) {
		Map<Tag, String> tagMap = new EnumMap<Tag, String>(Tag.class);
		for (Tag tag : Tag.values()) {
			if (values.containsKey(tag.getKey())) {
				tagMap.put(tag, values.get(tag.getKey()));
			}
		}
		return tagMap;

	}

	/**
	 * Convenience method used to convert the given string Tag value (returned
	 * from the external ExifTool process) into the type described by the
	 * associated {@link Tag}.
	 * 
	 * @param <T>
	 *            The type of the returned value.
	 * @param value
	 *            The {@link String} representation of the tag's value as parsed
	 *            from the image.
	 * 
	 * @return the given string value converted to a native Java type (e.g.
	 *         Integer, Double, etc.).
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>tag</code> is <code>null</code>.
	 * @throws NumberFormatException
	 *             if any exception occurs while trying to parse the given
	 *             <code>value</code> to any of the supported numeric types in
	 *             Java via calls to the respective <code>parseXXX</code>
	 *             methods defined on all the numeric wrapper classes (e.g.
	 *             {@link Integer#parseInt(String)} ,
	 *             {@link Double#parseDouble(String)} and so on).
	 * @throws ClassCastException
	 *             if the type defined by <code>T</code> is incompatible with
	 *             the type defined by {@link Tag#getType()} returned by the
	 *             <code>tag</code> argument passed in. This class performs an
	 *             implicit/unchecked cast to the type <code>T</code> before
	 *             returning the parsed result of the type indicated by
	 *             {@link Tag#getType()}. If the types do not match, a
	 *             <code>ClassCastException</code> will be generated by the VM.
	 */
	@SuppressWarnings("unchecked")
	public <T> T parseValue(String value) throws IllegalArgumentException {
		return (T) ExifTool.deserialize(getKey(), value, getType());
	}

	/**
	 * Used to get the name of the tag (e.g. "Orientation", "ISO", etc.).
	 * 
	 * @return the name of the tag (e.g. "Orientation", "ISO", etc.).
	 */
	public String getName() {
		return name;
	}

	/**
	 * Used to get a hint for the native type of this tag's value as specified
	 * by Phil Harvey's <a href=
	 * "http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html"
	 * >ExifTool Tag Guide</a>.
	 * 
	 * @return a hint for the native type of this tag's value.
	 */
	@Override
	public Class<?> getType() {
		return type;
	}

	@Override
	public String getKey() {
		return name;
	}

	@Override
	public boolean isMapped() {
		return true;
	}

	private String name;
	private Class<?> type;

	private Tag(String name, Class<?> type) {
		this.name = name;
		this.type = type;
	}
}