package com.thebuzzmedia.exiftool;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface ExifToolService extends AutoCloseable {
	/**
	 * Factory method with "best" ExifToolService implementation.
	 * 
	 * @author raisercostin
	 */
	public static class Factory {
		public static ExifToolService create(Feature... features) {
			return new ExifTool(features);
			// return new ExifToolNew2(features);
			// return new ExifToolNew(features);
		}

		public static ExifToolService create(int timeoutWhenKeepAliveInMillis,
				Feature... features) {
			return new ExifTool(timeoutWhenKeepAliveInMillis, features);
			// return new ExifToolNew(timeoutWhenKeepAliveInMillis, features);
			// return new ExifToolNew2(timeoutWhenKeepAliveInMillis, features);
		}

		public static ExifToolService create(ReadOptions readOptions,
				Feature... features) {
			//ignore readOptions
			return new ExifTool(features);
		}
	}

	/**
	 * Used to determine if the given {@link Feature} is supported by the
	 * underlying native install of ExifTool pointed at by {@link #exifCmd}.
	 * <p/>
	 * If support for the given feature has not been checked for yet, this
	 * method will automatically call out to ExifTool and ensure the requested
	 * feature is supported in the current local install.
	 * <p/>
	 * The external call to ExifTool to confirm feature support is only ever
	 * done once per JVM session and stored in a <code>static final</code>
	 * {@link Map} that all instances of this class share.
	 * 
	 * @param feature
	 *            The feature to check support for in the underlying ExifTool
	 *            install.
	 * 
	 * @return <code>true</code> if support for the given {@link Feature} was
	 *         confirmed to work with the currently installed ExifTool or
	 *         <code>false</code> if it is not supported.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>feature</code> is <code>null</code>.
	 * @throws RuntimeException
	 *             if any exception occurs while attempting to start the
	 *             external ExifTool process to verify feature support.
	 */
	boolean isFeatureSupported(Feature feature) throws RuntimeException;

	/**
	 * Used to startup the external ExifTool process and open the read/write
	 * streams used to communicate with it when {@link Feature#STAY_OPEN} is
	 * enabled. This method has no effect if the stay open feature is not
	 * enabled.
	 */
	void startup();

	/**
	 * This is same as {@link #close()}, added for consistency with
	 * {@link #startup()}
	 */
	void shutdown();

	/**
	 * Used to shutdown the external ExifTool process and close the read/write
	 * streams used to communicate with it when {@link Feature#STAY_OPEN} is
	 * enabled.
	 * <p/>
	 * <strong>NOTE</strong>: Calling this method does not preclude this
	 * instance of {@link ExifTool} from being re-used, it merely disposes of
	 * the native and internal resources until the next call to
	 * <code>getImageMeta</code> causes them to be re-instantiated.
	 * <p/>
	 * The cleanup thread will automatically call this after an interval of
	 * inactivity defined by {@link #processCleanupDelay}.
	 * <p/>
	 * Calling this method on an instance of this class without
	 * {@link Feature#STAY_OPEN} support enabled has no effect.
	 */
	void close();

	/**
	 * Using ExifTool in daemon mode (-stay_open True) executes different code
	 * paths below. So establish the flag for this once and it is reused a
	 * multitude of times later in this method to figure out where to branch to.
	 */
	boolean isStayOpen();

	/**
	 * For {@link ExifTool} instances with {@link Feature#STAY_OPEN} support
	 * enabled, this method is used to determine if there is currently a running
	 * ExifTool process associated with this class.
	 * <p/>
	 * Any dependent processes and streams can be shutdown using
	 * {@link #close()} and this class will automatically re-create them on the
	 * next call to <code>getImageMeta</code> if necessary.
	 * 
	 * @return <code>true</code> if there is an external ExifTool process in
	 *         daemon mode associated with this class utilizing the
	 *         {@link Feature#STAY_OPEN} feature, otherwise returns
	 *         <code>false</code>.
	 */
	boolean isRunning();

	/**
	 * Used to determine if the given {@link Feature} has been enabled for this
	 * particular instance of {@link ExifTool}.
	 * <p/>
	 * This method is different from {@link #isFeatureSupported(Feature)}, which
	 * checks if the given feature is supported by the underlying ExifTool
	 * install where as this method tells the caller if the given feature has
	 * been enabled for use in this particular instance.
	 * 
	 * @param feature
	 *            The feature to check if it has been enabled for us or not on
	 *            this instance.
	 * 
	 * @return <code>true</code> if the given {@link Feature} is currently
	 *         enabled on this instance of {@link ExifTool}, otherwise returns
	 *         <code>false</code>.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>feature</code> is <code>null</code>.
	 */
	boolean isFeatureEnabled(Feature feature) throws IllegalArgumentException;

	/** If no tags are given return all tags.*/
	Map<String, String> getImageMeta(File file, Format format,
			boolean supressDuplicates, String... tags) throws IOException;

	Map<MetadataTag, String> getImageMeta(File file, MetadataTag... tags)
			throws IllegalArgumentException, SecurityException, IOException;

	Map<Object, Object> getImageMeta2(File file, MetadataTag... tags)
			throws IllegalArgumentException, SecurityException, IOException;

	Map<MetadataTag, String> getImageMeta(File file, Format format,
			MetadataTag... tags) throws IllegalArgumentException,
			SecurityException, IOException;

	Map<String, String> getImageMeta(File file, Format format, TagGroup... tags)
			throws IllegalArgumentException, SecurityException, IOException;

	Map<Object, Object> readMetadata(File file, Object... tags)
			throws IOException;

	/**
	 * Reads metadata from the file.
	 */
	Map<Object, Object> readMetadata(ReadOptions options, File file,
			Object... tags) throws IOException;

	
	public <T> void addImageMetadata(File file, Map<T, Object> values)
			throws IOException;

	/**
	 * Takes a map of tags (either (@link Tag) or Strings for keys) and
	 * replaces/appends them to the metadata.
	 */
	<T> void writeMetadata(WriteOptions options, File file,
			Map<T, Object> values) throws IOException;

	/**
	 * extract image metadata to exiftool's internal xml format.
	 * 
	 * @param input
	 *            the input file
	 * @return command output as xml string
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	String getImageMetadataXml(File input, boolean includeBinary)
			throws IOException;

	/**
	 * extract image metadata to exiftool's internal xml format.
	 * 
	 * @param input
	 *            the input file
	 * @param output
	 *            the output file
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	void getImageMetadataXml(File input, File output, boolean includeBinary)
			throws IOException;

	/**
	 * output icc profile from input to output.
	 * 
	 * @param input
	 *            the input file
	 * @param output
	 *            the output file for icc data
	 * @return the command result from standard output e.g.
	 *         "1 output files created"
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	String extractImageIccProfile(File input, File output) throws IOException;

	/**
	 * Extract thumbnail from the given tag.
	 * 
	 * @param input
	 *            the input file
	 * @param tag
	 *            the tag containing binary data PhotoshopThumbnail or
	 *            ThumbnailImage
	 * @return the thumbnail file created. it is in the same folder as the input
	 *         file because of the syntax of exiftool and has the suffix
	 *         ".thumb.jpg"
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	File extractThumbnail(File input, Tag tag) throws IOException;

	void rebuildMetadata(File file) throws IOException;

	/**
	 * Rewrite all the the metadata tags in a JPEG image. This will not work for
	 * TIFF files. Use this when the image has some corrupt tags.
	 * 
	 * @link http://www.sno.phy.queensu.ca/~phil/exiftool/faq.html#Q20
	 */
	void rebuildMetadata(WriteOptions options, File file) throws IOException;
}