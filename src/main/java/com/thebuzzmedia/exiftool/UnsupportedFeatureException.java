package com.thebuzzmedia.exiftool;

/**
 * Class used to define an exception that occurs when the caller attempts to use
 * a {@link Feature} that the underlying native ExifTool install does not
 * support (i.e. the version isn't new enough).
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
public class UnsupportedFeatureException extends RuntimeException {
	private static final long serialVersionUID = -1332725983656030770L;

	private Feature feature;

	public UnsupportedFeatureException(Feature feature) {
		super(
				"Use of feature ["
						+ feature
						+ "] requires version "
						+ feature.getVersion()
						+ " or higher of the native ExifTool program. The version of ExifTool referenced by the system property 'exiftool.path' is not high enough. You can either upgrade the install of ExifTool or avoid using this feature to workaround this exception.");
	}

	public Feature getFeature() {
		return feature;
	}
}