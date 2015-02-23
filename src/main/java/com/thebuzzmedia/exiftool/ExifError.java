package com.thebuzzmedia.exiftool;


/**
 * Represents an error from the ExifToolNew3
 * 
 * @author msgile
 * @author $LastChangedBy$
 * @version $Revision$ $LastChangedDate$
 * @since 7/25/14
 */
public class ExifError extends RuntimeException {
	public ExifError(String message) {
		super(message);
	}

	public ExifError(String message, Throwable cause) {
		super(message, cause);
	}
}
