package com.thebuzzmedia.exiftool;

// ================================================================================
public enum TagGroup {
	EXIF("EXIF", "exif:all"),
	IPTC("IPTC", "iptc:all"),
	XMP("XMP", "xmp:all"),
	ALL("ALL", "all"),
	FILE("FILE", "file:all"),
	ICC("ICC", "icc_profile:all");

	private final String name;
	private final String value;

	private TagGroup(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public String getValue() {
		return value;
	}
}