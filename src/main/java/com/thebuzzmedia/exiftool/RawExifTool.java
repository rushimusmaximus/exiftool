package com.thebuzzmedia.exiftool;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.thebuzzmedia.exiftool.adapters.ExifToolService;

/**
 * Interface that retrieves the raw data from exiftool. Adding semantic oriented details like, types, format etc should
 * be done outside of this class.
 */
public interface RawExifTool extends AutoCloseable {
	/**
	 * Factory method with "best" ExifToolService implementation.
	 * 
	 * @author raisercostin
	 */
	public static class Factory {
		public static ExifToolService create(Feature... features) {
			return new ExifToolService(new ExifToolNew3(features));
			// return new ExifToolNew2(features);
			// return new ExifToolNew(features);
		}

		public static ExifToolService create(int timeoutWhenKeepAliveInMillis, Feature... features) {
			return new ExifToolService(new ExifToolNew3(timeoutWhenKeepAliveInMillis, features));
			// return new ExifToolNew(timeoutWhenKeepAliveInMillis, features);
			// return new ExifToolNew2(timeoutWhenKeepAliveInMillis, features);
		}

		public static ExifToolService create(ReadOptions readOptions, Feature... features) {
			// ignore readOptions
			return new ExifToolService(new ExifToolNew3(features));
		}
	}

	/**
	 * Used to determine if the given {@link Feature} is supported by the underlying native install of ExifToolNew3
	 * pointed at by {@link #exifCmd}.
	 * <p/>
	 * If support for the given feature has not been checked for yet, this method will automatically call out to
	 * ExifToolNew3 and ensure the requested feature is supported in the current local install.
	 * <p/>
	 * The external call to ExifToolNew3 to confirm feature support is only ever done once per JVM session and stored in
	 * a <code>static final</code> {@link Map} that all instances of this class share.
	 * 
	 * @param feature
	 *            The feature to check support for in the underlying ExifToolNew3 install.
	 * 
	 * @return <code>true</code> if support for the given {@link Feature} was confirmed to work with the currently
	 *         installed ExifToolNew3 or <code>false</code> if it is not supported.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>feature</code> is <code>null</code>.
	 * @throws RuntimeException
	 *             if any exception occurs while attempting to start the external ExifToolNew3 process to verify feature
	 *             support.
	 */
	boolean isFeatureSupported(Feature feature) throws RuntimeException;

	/**
	 * Used to startup the external ExifToolNew3 process and open the read/write streams used to communicate with it
	 * when {@link Feature#STAY_OPEN} is enabled. This method has no effect if the stay open feature is not enabled.
	 */
	void startup();

	/**
	 * This is same as {@link #close()}, added for consistency with {@link #startup()}
	 */
	void shutdown();

	/**
	 * Used to shutdown the external ExifToolNew3 process and close the read/write streams used to communicate with it
	 * when {@link Feature#STAY_OPEN} is enabled.
	 * <p/>
	 * <strong>NOTE</strong>: Calling this method does not preclude this instance of {@link ExifToolNew3} from being
	 * re-used, it merely disposes of the native and internal resources until the next call to <code>getImageMeta</code>
	 * causes them to be re-instantiated.
	 * <p/>
	 * The cleanup thread will automatically call this after an interval of inactivity defined by
	 * {@link #processCleanupDelay}.
	 * <p/>
	 * Calling this method on an instance of this class without {@link Feature#STAY_OPEN} support enabled has no effect.
	 */
	void close();

	/**
	 * Using ExifToolNew3 in daemon mode (-stay_open True) executes different code paths below. So establish the flag
	 * for this once and it is reused a multitude of times later in this method to figure out where to branch to.
	 */
	boolean isStayOpen();

	/**
	 * For {@link ExifToolNew3} instances with {@link Feature#STAY_OPEN} support enabled, this method is used to
	 * determine if there is currently a running ExifToolNew3 process associated with this class.
	 * <p/>
	 * Any dependent processes and streams can be shutdown using {@link #close()} and this class will automatically
	 * re-create them on the next call to <code>getImageMeta</code> if necessary.
	 * 
	 * @return <code>true</code> if there is an external ExifToolNew3 process in daemon mode associated with this class
	 *         utilizing the {@link Feature#STAY_OPEN} feature, otherwise returns <code>false</code>.
	 */
	boolean isRunning();

	/**
	 * Used to determine if the given {@link Feature} has been enabled for this particular instance of
	 * {@link ExifToolNew3}.
	 * <p/>
	 * This method is different from {@link #isFeatureSupported(Feature)}, which checks if the given feature is
	 * supported by the underlying ExifToolNew3 install where as this method tells the caller if the given feature has
	 * been enabled for use in this particular instance.
	 * 
	 * @param feature
	 *            The feature to check if it has been enabled for us or not on this instance.
	 * 
	 * @return <code>true</code> if the given {@link Feature} is currently enabled on this instance of
	 *         {@link ExifToolNew3}, otherwise returns <code>false</code>.
	 * 
	 * @throws IllegalArgumentException
	 *             if <code>feature</code> is <code>null</code>.
	 */
	boolean isFeatureEnabled(Feature feature) throws IllegalArgumentException;

	public <T> void addImageMetadata(File file, Map<T, Object> values) throws IOException;

	/**
	 * Takes a map of tags (either (@link Tag) or Strings for keys) and replaces/appends them to the metadata.
	 */
	<T> void writeMetadata(WriteOptions options, File file, Map<T, Object> values) throws IOException;

	void rebuildMetadata(File file) throws IOException;

	/**
	 * Rewrite all the the metadata tags in a JPEG image. This will not work for TIFF files. Use this when the image has
	 * some corrupt tags.
	 * 
	 * @link http://www.sno.phy.queensu.ca/~phil/exiftool/faq.html#Q20
	 */
	void rebuildMetadata(WriteOptions options, File file) throws IOException;

	/** If no tags are given return all tags. */
	Map<String, String> getImageMeta(File file, ReadOptions readOptions, String... tags)
			throws IOException;

	List<String> execute(List<String> args);
}
