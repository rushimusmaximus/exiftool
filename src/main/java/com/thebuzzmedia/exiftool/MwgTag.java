package com.thebuzzmedia.exiftool;

import java.util.Date;

//================================================================================
public enum MwgTag implements MetadataTag {
	LOCATION("Location", String.class),
	CITY("City", String.class),
	STATE("State", String.class),
	COUNTRY("Country", String.class),

	COPYRIGHT("Copyright", String.class),

	DATE_TIME_ORIGINAL("DateTimeOriginal", Date.class),
	CREATE_DATE("CreateDate", Date.class),
	MODIFY_DATE("ModifyDate", Date.class),

	CREATOR("Creator", String.class),
	DESCRIPTION("Description", String.class),
	KEYWORDS("Keywords", String[].class),

	ORIENTATION("Orientation", Integer.class),
	RATING("Rating", Integer.class),

	;

	private String name;
	private Class<?> type;

	private MwgTag(String name, Class type) {
		this.name = name;
		this.type = type;
	}

	@Override
	public String getKey() {
		return name;
	}

	@Override
	public Class<?> getType() {
		return type;
	}

	@Override
	public boolean isMapped() {
		return true;
	}

	@Override
	public String toString() {
		return name;
	}
}