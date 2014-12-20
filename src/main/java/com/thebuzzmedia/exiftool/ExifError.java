package com.thebuzzmedia.exiftool;

import java.io.IOException;

/**
 * Represents an error from the ExifToolNew3
 * 
 * @author msgile
 * @author $LastChangedBy$
 * @version $Revision$ $LastChangedDate$
 * @since 7/25/14
 */
public class ExifError extends IOException {
	public ExifError(String message) {
		super(message);
	}
}
