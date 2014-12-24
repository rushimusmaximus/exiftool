package com.thebuzzmedia.exiftool;

/**
 * A Custom Tag that the user defines. Used to cover tags not in the enum.
 */
public class CustomTag implements MetadataTag {
	private final String name;
	private final Class type;
	private final boolean mapped;

	public CustomTag(String name, Class type) {
		this(name, type, !name.trim().endsWith(":all"));
	}

	public CustomTag(String name, Class type, boolean mapped) {
		this.name = name.trim();
		this.type = type;
		this.mapped = mapped;
	}

	@Override
	public String getKey() {
		return name;
	}

	@Override
	public Class getType() {
		return type;
	}

	@Override
	public boolean isMapped() {
		return mapped;
	}

	@Override
	public String toString() {
		return getKey();
	}

	@Override
	public <T> String toExif(T value) {
		return Tag.toExif(this, value);
	}
}