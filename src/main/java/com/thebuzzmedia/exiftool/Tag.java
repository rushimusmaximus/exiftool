package com.thebuzzmedia.exiftool;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

// ================================================================================
/**
 * Enum used to pre-define a convenient list of tags that can be easily extracted from images using this class with an
 * external install of ExifToolNew3.
 * <p/>
 * Each tag defined also includes a type hint for the parsed value associated with it when the default
 * {@link Format#NUMERIC} value format is used.
 * <p/>
 * All replies from ExifToolNew3 are parsed as {@link String}s and using the type hint from each {@link Tag} can easily
 * be converted to the correct data format by using the provided {@link Tag#parseValue(String)} method.
 * <p/>
 * This class does not make an attempt at converting the value automatically in case the caller decides they would
 * prefer tag values returned in {@link Format#HUMAN_READABLE} format and to avoid any compatibility issues with future
 * versions of ExifToolNew3 if a tag's return value is changed. This approach to leaving returned tag values as strings
 * until the caller decides they want to parse them is a safer and more robust approach.
 * <p/>
 * The types provided by each tag are merely a hint based on the <a
 * href="http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html" >ExifToolNew3 Tag Guide</a> by Phil Harvey;
 * the caller is free to parse or process the returned {@link String} values any way they wish.
 * <h3>Tag Support</h3>
 * ExifToolNew3 is capable of parsing almost every tag known to man (1000+), but this class makes an attempt at
 * pre-defining a convenient list of the most common tags for use.
 * <p/>
 * This list was determined by looking at the common metadata tag values written to images by popular mobile devices
 * (iPhone, Android) as well as cameras like simple point and shoots as well as DSLRs. As an additional source of input
 * the list of supported/common EXIF formats that Flickr supports was also reviewed to ensure the most common/useful
 * tags were being covered here.
 * <p/>
 * Please email me or <a href="https://github.com/thebuzzmedia/imgscalr/issues">file an issue</a> if you think this list
 * is missing a commonly used tag that should be added to it.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
public enum Tag implements MetadataTag {
	// single entry tags
	APERTURE("ApertureValue", Double.class),
	ARTIST("Artist", String.class),
	AUTHOR("XPAuthor", String.class),
	AVG_BITRATE("AvgBitrate", String.class),
	BODY_SERIAL_NUMBER("BodySerialNumber", String.class),
	CAMERA_SERIAL_NUMBER("CameraSerialNumber", String.class),
	CAPTION_ABSTRACT("Caption-Abstract", String.class),
	COLOR_SPACE("ColorSpace", Integer.class),
	COMMENT("XPComment", String.class),
	COMMENTS("Comment", String.class),
	CONTENT_CREATION_DATE("ContentCreateDate", Date.class),
	CONTRAST("Contrast", Integer.class),
	COPYRIGHT("Copyright", String.class),
	COPYRIGHT_NOTICE("CopyrightNotice", String.class),
	CREATE_DATE("CreateDate", Date.class),
	CREATION_DATE("CreationDate", Date.class),
	CREATOR("Creator", String.class),
	DATE_CREATED("DateCreated", Date.class),
	DATE_TIME_ORIGINAL("DateTimeOriginal", Date.class),
	DEVICE_SERIAL_NUMBER("DeviceSerialNumber", String.class),
	DIGITAL_ZOOM_RATIO("DigitalZoomRatio", Double.class),
	EXIF_VERSION("ExifVersion", String.class),
	EXPOSURE_COMPENSATION("ExposureCompensation", Double.class),
	EXPOSURE_PROGRAM("ExposureProgram", Integer.class),
	EXPOSURE_TIME("ExposureTime", Double.class),
	EXTENDER_SERIAL_NUMBER("ExtenderSerialNumber", String.class),
	FILE_TYPE("FileType", String.class),
	FLASH("Flash", Integer.class),
	FLASH_SERIAL_NUMBER("FlashSerialNumber", String.class),
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
	IMAGE_UNIQUE_ID("ImageUniqueID", String.class),
	IMAGE_WIDTH("ImageWidth", Integer.class),
	INTERNAL_SERIAL_NUMBER("InternalSerialNumber", String.class),
	IPTC_KEYWORDS("Keywords", String.class),
	ISO("ISO", Integer.class),
	KEYWORDS("XPKeywords", String.class),
	LENS("Lens", String.class),
	LENS_ID("LensID", String.class),
	LENS_MAKE("LensMake", String.class),
	LENS_MODEL("LensModel", String.class),
	LENS_SERIAL_NUMBER("LensSerialNumber", String.class),
	MAKE("Make", String.class),
	METERING_MODE("MeteringMode", Integer.class),
	MIME_TYPE("MIMEType", String.class),
	MODEL("Model", String.class),
	OBJECT_NAME("ObjectName", String.class),
	ORIENTATION("Orientation", Integer.class),
	OWNER_NAME("OwnerName", String.class),
	RATING("Rating", Integer.class),
	RATING_PERCENT("RatingPercent", Integer.class),
	ROTATION("Rotation", Integer.class),
	SATURATION("Saturation", Integer.class),
	SCANNER_SERIAL_NUMBER("ScannerSerialNumber", String.class),
	SENSING_METHOD("SensingMethod", Integer.class),
	SERIAL_NUMBER("SerialNumber", String.class),
	SHARPNESS("Sharpness", Integer.class),
	SHUTTER_SPEED("ShutterSpeedValue", Double.class),
	SOFTWARE("Software", String.class),
	SOURCE_SERIAL_NUMBER("SourceSerialNumber", String.class),
	SUB_SEC_TIME_ORIGINAL("SubSecTimeOriginal", Integer.class),
	SUB_SEC_DATE_TIME_ORIGINAL("SubSecDateTimeOriginal", Date.class),
	SUBJECT("XPSubject", String.class),
	TITLE("XPTitle", String.class),
	WHITE_BALANCE("WhiteBalance", Integer.class),
	X_RESOLUTION("XResolution", Double.class),
	Y_RESOLUTION("YResolution", Double.class),
	// select ICC metadata
	ICC_DESCRIPTION("ProfileDescription", String.class),
	ICC_COLORSPACEDATA("ColorSpaceData", String.class),
	// actually binary data, but what are we doing to do here??? Just use to
	// save to file...
	THUMBNAIL_IMAGE("ThumbnailImage", String.class),
	THUMBNAIL_PHOTOSHOP("PhotoshopThumbnail", String.class), ;

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Tag.class);
	private static final Map<String, Tag> TAG_LOOKUP_MAP;

	/**
	 * Initializer used to init the <code>static final</code> tag/name lookup map used by all instances of this class.
	 */
	static {
		Tag[] values = Tag.values();
		TAG_LOOKUP_MAP = new HashMap<String, Tag>(values.length * 3);

		for (int i = 0; i < values.length; i++) {
			Tag tag = values[i];
			TAG_LOOKUP_MAP.put(tag.getKey(), tag);
		}
	}

	/**
	 * Used to get the {@link Tag} identified by the given, case-sensitive, tag name.
	 * 
	 * @param name
	 *            The case-sensitive name of the tag that will be searched for.
	 * 
	 * @return the {@link Tag} identified by the given, case-sensitive, tag name or <code>null</code> if one couldn't be
	 *         found.
	 */
	public static Tag forName(String name) {
		return TAG_LOOKUP_MAP.get(name);
	}

	public static Map<MetadataTag, String> toTagMap(Map<String, String> values) {
		return mapByTag(values);
	}

	private static Map<MetadataTag, String> mapByTag(Map<String, String> stringMap) {
		Map<MetadataTag, String> tagMap = new HashMap<MetadataTag, String>(Tag.values().length);
		for (Tag tag : Tag.values()) {
			if (stringMap.containsKey(tag.getKey())) {
				tagMap.put(tag, stringMap.get(tag.getKey()));
			}
		}
		return tagMap;
	}

	/**
	 * Convenience method used to convert the given string Tag value (returned from the external ExifToolNew3 process)
	 * into the type described by the associated {@link Tag}.
	 * 
	 * @param <T>
	 *            The type of the returned value.
	 * @param value
	 *            The {@link String} representation of the tag's value as parsed from the image.
	 * 
	 * @return the given string value converted to a native Java type (e.g. Integer, Double, etc.).
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>tag</code> is <code>null</code>.
	 * @throws NumberFormatException
	 *             if any exception occurs while trying to parse the given <code>value</code> to any of the supported
	 *             numeric types in Java via calls to the respective <code>parseXXX</code> methods defined on all the
	 *             numeric wrapper classes (e.g. {@link Integer#parseInt(String)} , {@link Double#parseDouble(String)}
	 *             and so on).
	 * @throws ClassCastException
	 *             if the type defined by <code>T</code> is incompatible with the type defined by {@link Tag#getType()}
	 *             returned by the <code>tag</code> argument passed in. This class performs an implicit/unchecked cast
	 *             to the type <code>T</code> before returning the parsed result of the type indicated by
	 *             {@link Tag#getType()}. If the types do not match, a <code>ClassCastException</code> will be generated
	 *             by the VM.
	 */
	@SuppressWarnings("unchecked")
	public <T> T parseValue(String value) throws IllegalArgumentException {
		return parseValue((Class<T>) getType(), value);
	}

	public String getRawValue(Map<MetadataTag, String> metadata) {
		return metadata.get(this);
	}

	@SuppressWarnings("unchecked")
	public <K, V1, V2> V2 getValue(Map<K, V1> metadata) {
		return (V2) parseValue(this, metadata.get(this));
	}

	@SuppressWarnings("unchecked")
	public static <T> T parseValue(MetadataTag tag, Object value) throws IllegalArgumentException,
			NumberFormatException {
		if (tag == null)
			throw new IllegalArgumentException("tag cannot be null");
		Class<T> type = tag.getType();
		return parseValue(type, value);
	}

	@SuppressWarnings("unchecked")
	public static <T> String toExif(MetadataTag tag, Object value) throws IllegalArgumentException,
			NumberFormatException {
		String result = null;
		if (value == null) {
			// nothing to do
		} else {
			Class<T> type = tag.getType();
			if (Date.class.equals(type)) {
				if (!Date.class.equals(value.getClass())) {
					value = parseValue(tag, value);
				}
				SimpleDateFormat formatter = new SimpleDateFormat(ExifToolNew.EXIF_DATE_FORMAT);
				try {
					result = formatter.format(value);
				} catch (IllegalArgumentException e) {
					throw new ExifError("Cannot convert [" + value + "] of type " + value.getClass()
							+ " to date using formatter [" + ExifToolNew.EXIF_DATE_FORMAT + "]", e);
				}
			} else if (String[].class.equals(type)) {
				// @see http://www.sno.phy.queensu.ca/~phil/exiftool/faq.html - 17.
				// "List-type tags do not behave as expected"
				// result = Joiner.on(", ").join((String[])value);
				result = value.toString();
			} else {
				result = value.toString();
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <T> T parseValue(Class<T> type, Object value) throws IllegalArgumentException, NumberFormatException {
		T result = null;
		if (value == null) {
			// nothing to do
		} else if (type == value.getClass()) {
			result = (T) value;
		} else if (Boolean.class.isAssignableFrom(type))
			result = (T) Boolean.valueOf(value.toString());
		else if (Byte.class.isAssignableFrom(type))
			result = (T) Byte.valueOf(Byte.parseByte(value.toString()));
		else if (Integer.class.isAssignableFrom(type))
			result = (T) Integer.valueOf(Integer.parseInt(value.toString()));
		else if (Short.class.isAssignableFrom(type))
			result = (T) Short.valueOf(Short.parseShort(value.toString()));
		else if (Long.class.isAssignableFrom(type))
			result = (T) Long.valueOf(Long.parseLong(value.toString()));
		else if (Float.class.isAssignableFrom(type))
			result = (T) new Float(parseDouble(value.toString()).floatValue());
		else if (Double.class.isAssignableFrom(type))
			result = (T) parseDouble(value.toString());
		else if (Character.class.isAssignableFrom(type))
			result = (T) Character.valueOf(value.toString().charAt(0));
		else if (String.class.isAssignableFrom(type))
			result = (T) value.toString();
		else if (String[].class.equals(type)) {
			// @see http://www.sno.phy.queensu.ca/~phil/exiftool/faq.html - 17.
			// "List-type tags do not behave as expected"
			result = (T) Splitter.on(", ").splitToList(value.toString()).toArray(new String[0]);
		} else if (Date.class.equals(type)) {
			SimpleDateFormat formatter = new SimpleDateFormat(ExifToolNew.EXIF_DATE_FORMAT);
			try {
				result = (T) formatter.parse(value.toString());
			} catch (ParseException e) {
				try {
					long value2 = Long.parseLong(value.toString());
					result = (T)new Date(value2);
				} catch (NumberFormatException e2) {
					throw new ExifError("Can't parse value " + value + " with format ["
							+ ExifToolNew.EXIF_DATE_FORMAT + "] and neither as a number of miliseconds from ["+new Date(0)+"].", e);
				}
			}
		} else
			result = (T) value;
		return result;
	}

	private static Double parseDouble(String in) {
		if (in.contains("/")) {
			String[] enumeratorAndDivisor = in.split("/");
			return Double.parseDouble(enumeratorAndDivisor[0]) / Double.parseDouble(enumeratorAndDivisor[1]);
		} else {
			return Double.parseDouble(in);
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T parseValue2(String value) throws IllegalArgumentException {
		return (T) deserialize(getKey(), value, getType());
	}

	public static Object deserialize(String tagName, String value, Class expectedType) {
		try {
			if (Boolean.class.equals(expectedType)) {
				if (value == null)
					return null;
				value = value.trim().toLowerCase();
				switch (value.charAt(0)) {
				case 'n':
				case 'f':
				case '0':
					return false;
				}
				if (value.equals("off")) {
					return false;
				}
				return true;
			} else if (Date.class.equals(expectedType)) {
				if (value == null)
					return null;
				SimpleDateFormat formatter = new SimpleDateFormat(ExifToolNew.EXIF_DATE_FORMAT);
				return formatter.parse(value);
			} else if (Integer.class.equals(expectedType)) {
				if (value == null)
					return 0;
				return Integer.parseInt(value);
			} else if (Long.class.equals(expectedType)) {
				if (value == null)
					return 0;
				return Long.parseLong(value);
			} else if (Float.class.equals(expectedType)) {
				if (value == null)
					return 0;
				return Float.parseFloat(value);
			} else if (Double.class.equals(expectedType)) {
				if (value == null)
					return 0;
				String[] enumeratorAndDivisor = value.split("/");
				if (enumeratorAndDivisor.length == 2) {
					return Double.parseDouble(enumeratorAndDivisor[0]) / Double.parseDouble(enumeratorAndDivisor[1]);
				} else {
					return Double.parseDouble(value);
				}
			} else if (String[].class.equals(expectedType)) {
				if (value == null)
					return new String[0];
				return value.split(",");
			} else {
				return value;
			}
		} catch (ParseException ex) {
			LOG.warn("Invalid format, Tag:" + tagName);
			return null;
		} catch (NumberFormatException ex) {
			LOG.warn("Invalid format, Tag:" + tagName);
			return null;
		}

	}

	@Deprecated
	public String getName() {
		return getKey();
	}

	/**
	 * Used to get a hint for the native type of this tag's value as specified by Phil Harvey's <a href=
	 * "http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html" >ExifToolNew3 Tag Guide</a>.
	 * 
	 * @return a hint for the native type of this tag's value.
	 */
	@Override
	public Class<?> getType() {
		return type;
	}

	/**
	 * Used to get the name of the tag (e.g. "Orientation", "ISO", etc.).
	 * 
	 * @return the name of the tag (e.g. "Orientation", "ISO", etc.).
	 */
	@Override
	public String getKey() {
		return key;
	}

	@Override
	public boolean isMapped() {
		return true;
	}

	private String key;
	private Class<?> type;

	private Tag(String key, Class<?> type) {
		this.key = key;
		this.type = type;
	}

	@Override
	public <T> String toExif(T value) {
		return toExif(this, value);
	}
}