package com.thebuzzmedia.exiftool;

// ================================================================================
public enum TagGroup implements MetadataTag {
	EXIF("EXIF", "exif:all"),
	IPTC("IPTC", "iptc:all"),
	XMP("XMP", "xmp:all"),
	ALL("ALL", "all"),
	FILE("FILE", "file:all"),
	ICC("ICC", "icc_profile:all");

	private final String name;
	private final String key;

	private TagGroup(String name, String key) {
		this.name = name;
		this.key = key;
	}

	public String getName() {
		return name;
	}
	@Deprecated
	public String getValue() {
		return getKey();
	}

	@Override
	public String getKey() {
		return key;
	}

	@Override
	public Class getType() {
		return Void.class;
	}

	@Override
	public boolean isMapped() {
		return false;
	}

}