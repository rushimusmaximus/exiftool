/**
 * Copyright 2011 The Buzz Media, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thebuzzmedia.exiftool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide a Java-like interface to Phil Harvey's excellent, Perl-based <a
 * href="http://www.sno.phy.queensu.ca/~phil/exiftool">ExifTool</a>.
 * <p/>
 * Initial work done by "Riyad Kalla" software@thebuzzmedia.com.
 * <p/>
 * There are a number of other basic Java wrappers to ExifTool available online,
 * but most of them only abstract out the actual Java-external-process execution
 * logic and do no additional work to make integration with the external
 * ExifTool any easier or intuitive from the perspective of the Java application
 * written to make use of ExifTool.
 * <p/>
 * This class was written in order to make integration with ExifTool inside of a
 * Java application seamless and performant with the goal being that the
 * developer can treat ExifTool as if it were written in Java, garnering all of
 * the benefits with none of the added headache of managing an external native
 * process from Java.
 * <p/>
 * Phil Harvey's ExifTool is written in Perl and runs on all major platforms
 * (including Windows) so no portability issues are introduced into your
 * application by utilizing this class.
 * <h3>Usage</h3>
 * Assuming ExifTool is installed on the host system correctly and either in the
 * system path or pointed to by {@link #ENV_EXIF_TOOL_PATH}, using this class to
 * communicate with ExifTool is as simple as creating an instance (
 * <code>ExifTool tool = new ExifTool()</code>) and then making calls to
 * {@link #readMetadata(ReadOptions,java.io.File, Object...)} (optionally
 * supplying tags or
 * {@link #writeMetadata(WriteOptions,java.io.File, java.util.Map)}
 * <p/>
 * In this default mode methods will automatically start an external ExifTool
 * process to handle the request. After ExifTool has parsed the tag values from
 * the file, the external process exits and this class parses the result before
 * returning it to the caller.
 * <p/>
 * <h3>ExifTool -stay_open Support</h3>
 * ExifTool <a href=
 * "http://u88.n24.queensu.ca/exiftool/forum/index.php/topic,1402.msg12933.html#msg12933"
 * >8.36</a> added a new persistent-process feature that allows ExifTool to stay
 * running in a daemon mode and continue accepting commands via a file or stdin.
 * <p/>
 * This new mode is controlled via the <code>-stay_open True/False</code>
 * command line argument and in a busy system that is making thousands of calls
 * to ExifTool, can offer speed improvements of up to <strong>60x</strong> (yes,
 * really that much).
 * <p/>
 * This feature was added to ExifTool shortly after user <a
 * href="http://www.christian-etter.de/?p=458">Christian Etter discovered</a>
 * the overhead for starting up a new Perl interpreter each time ExifTool is
 * loaded accounts for roughly <a href=
 * "http://u88.n24.queensu.ca/exiftool/forum/index.php/topic,1402.msg6121.html#msg6121"
 * >98.4% of the total runtime</a>.
 * <p/>
 * Support for using ExifTool in daemon mode is enabled by passing
 * {@link Feature#STAY_OPEN} to the constructor of the class when creating an
 * instance of this class and then simply using the class as you normally would.
 * This class will manage a single ExifTool process running in daemon mode in
 * the background to service all future calls to the class.
 * <p/>
 * Because this feature requires ExifTool 8.36 or later, this class will
 * actually verify support for the feature in the version of ExifTool pointed at
 * by {@link #ENV_EXIF_TOOL_PATH} before successfully instantiating the class
 * and will notify you via an {@link UnsupportedFeatureException} if the native
 * ExifTool doesn't support the requested feature.
 * <p/>
 * In the event of an {@link UnsupportedFeatureException}, the caller can either
 * upgrade the native ExifTool upgrade to the version required or simply avoid
 * using that feature to work around the exception.
 * <h3>Automatic Resource Cleanup</h3>
 * When {@link Feature#STAY_OPEN} mode is used, there is the potential for
 * leaking both host OS processes (native 'exiftool' processes) as well as the
 * read/write streams used to communicate with it unless {@link #close()} is
 * called to clean them up when done. <strong>Fortunately</strong>, this class
 * provides an automatic cleanup mechanism that runs, by default, after 10mins
 * of inactivity to clean up those stray resources.
 * <p/>
 * The inactivity period can be controlled by modifying the
 * {@link #ENV_EXIF_TOOL_PROCESSCLEANUPDELAY} system variable. A value of
 * <code>0</code> or less disabled the automatic cleanup process and requires
 * you to cleanup ExifTool instances on your own by calling {@link #close()}
 * manually.
 * <p/>
 * Any class activity by way of calls to <code>getImageMeta</code> will always
 * reset the inactivity timer, so in a busy system the cleanup thread could
 * potentially never run, leaving the original host ExifTool process running
 * forever (which is fine).
 * <p/>
 * This design was chosen to help make using the class and not introducing
 * memory leaks and bugs into your code easier as well as making very inactive
 * instances of this class light weight while not in-use by cleaning up after
 * themselves.
 * <p/>
 * The only overhead incurred when opening the process back up is a 250-500ms
 * lag while launching the VM interpreter again on the first call (depending on
 * host machine speed and load).
 * <h3>Reusing a "closed" ExifTool Instance</h3>
 * If you or the cleanup thread have called {@link #close()} on an instance of
 * this class, cleaning up the host process and read/write streams, the instance
 * of this class can still be safely used. Any followup calls to
 * <code>getImageMeta</code> will simply re-instantiate all the required
 * resources necessary to service the call (honoring any {@link Feature}s set).
 * <p/>
 * This can be handy behavior to be aware of when writing scheduled processing
 * jobs that may wake up every hour and process thousands of pictures then go
 * back to sleep. In order for the process to execute as fast as possible, you
 * would want to use ExifTool in daemon mode (pass {@link Feature#STAY_OPEN} to
 * the constructor of this class) and when done, instead of {@link #close()}-ing
 * the instance of this class and throwing it out, you can keep the reference
 * around and re-use it again when the job executes again an hour later.
 * <h3>Performance</h3>
 * Extra care is taken to ensure minimal object creation or unnecessary CPU
 * overhead while communicating with the external process.
 * <p/>
 * {@link Pattern}s used to split the responses from the process are explicitly
 * compiled and reused, string concatenation is minimized, Tag name lookup is
 * done via a <code>static final</code> {@link Map} shared by all instances and
 * so on.
 * <p/>
 * Additionally, extra care is taken to utilize the most optimal code paths when
 * initiating and using the external process, for example, the
 * {@link ProcessBuilder#command(List)} method is used to avoid the copying of
 * array elements when {@link ProcessBuilder#command(String...)} is used and
 * avoiding the (hidden) use of {@link StringTokenizer} when
 * {@link Runtime#exec(String)} is called.
 * <p/>
 * All of this effort was done to ensure that imgscalr and its supporting
 * classes continue to provide best-of-breed performance and memory utilization
 * in long running/high performance environments (e.g. web applications).
 * <h3>Thread Safety</h3>
 * Instances of this class are <strong>not</strong> Thread-safe. Both the
 * instance of this class and external ExifTool process maintain state specific
 * to the current operation. Use of instances of this class need to be
 * synchronized using an external mechanism or in a highly threaded environment
 * (e.g. web application), instances of this class can be used along with
 * {@link ThreadLocal}s to ensure Thread-safe, highly parallel use.
 * <h3>Why ExifTool?</h3>
 * <a href="http://www.sno.phy.queensu.ca/~phil/exiftool">ExifTool</a> is
 * written in Perl and requires an external process call from Java to make use
 * of.
 * <p/>
 * While this would normally preclude a piece of software from inclusion into
 * the imgscalr library (more complex integration), there is no other image
 * metadata piece of software available as robust, complete and well-tested as
 * ExifTool. In addition, ExifTool already runs on all major platforms
 * (including Windows), so there was not a lack of portability introduced by
 * providing an integration for it.
 * <p/>
 * Allowing it to be used from Java is a boon to any Java project that needs the
 * ability to read/write image-metadata from almost <a
 * href="http://www.sno.phy.queensu.ca/~phil/exiftool/#supported">any image or
 * video file</a> format.
 * <h3>Alternatives</h3>
 * If integration with an external Perl process is something your app cannot do
 * and you still need image metadata-extraction capability, Drew Noakes has
 * written the 2nd most robust image metadata library I have come across: <a
 * href="http://drewnoakes.com/drewnoakes.com/code/exif/">Metadata Extractor</a>
 * that you might want to look at.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
public class ExifToolNew implements ExifToolService {

	/**
	 * If ExifTool is on your system path and running the command "exiftool"
	 * successfully executes it, the default value unchanged will work fine on
	 * any platform. If the ExifTool executable is named something else or not
	 * in the system path, then this property will need to be set to point at it
	 * before using this class.
	 * <p/>
	 * This system property can be set on startup with:<br/>
	 * <code>
	 * -Dexiftool.path=/path/to/exiftool
	 * </code> or by calling {@link System#setProperty(String, String)} before
	 * this class is loaded.
	 * <p/>
	 * On Windows be sure to double-escape the path to the tool, for example:
	 * <code>
	 * -Dexiftool.path=C:\\Tools\\exiftool.exe
	 * </code>
	 * <p/>
	 * Default value is "<code>exiftool</code>".
	 * <h3>Relative Paths</h3>
	 * Relative path values (e.g. "bin/tools/exiftool") are executed with
	 * relation to the base directory the VM process was started in. Essentially
	 * the directory that <code>new File(".").getAbsolutePath()</code> points at
	 * during runtime.
	 */
	private static final String ENV_EXIF_TOOL_PATH = "exiftool.path";
	/**
	 * Interval (in milliseconds) of inactivity before the cleanup thread wakes
	 * up and cleans up the daemon ExifTool process and the read/write streams
	 * used to communicate with it when the {@link Feature#STAY_OPEN} feature is
	 * used.
	 * <p/>
	 * Ever time a call to <code>getImageMeta</code> is processed, the timer
	 * keeping track of cleanup is reset; more specifically, this class has to
	 * experience no activity for this duration of time before the cleanup
	 * process is fired up and cleans up the host OS process and the stream
	 * resources.
	 * <p/>
	 * Any subsequent calls to <code>getImageMeta</code> after a cleanup simply
	 * re-initializes the resources.
	 * <p/>
	 * This system property can be set on startup with:<br/>
	 * <code>
	 * -Dexiftool.processCleanupDelay=600000
	 * </code> or by calling {@link System#setProperty(String, String)} before
	 * this class is loaded.
	 * <p/>
	 * Setting this value to 0 disables the automatic cleanup thread completely
	 * and the caller will need to manually cleanup the external ExifTool
	 * process and read/write streams by calling {@link #close()}.
	 * <p/>
	 * Default value is zero, no inactivity timeout.
	 */
	static final String ENV_EXIF_TOOL_PROCESSCLEANUPDELAY = "exiftool.processCleanupDelay";
	static final long DEFAULT_PROCESS_CLEANUP_DELAY = 0;

	/**
	 * Name used to identify the (optional) cleanup {@link Thread}.
	 * <p/>
	 * This is only provided to make debugging and profiling easier for
	 * implementers making use of this class such that the resources this class
	 * creates and uses (i.e. Threads) are readily identifiable in a running VM.
	 * <p/>
	 * Default value is "<code>ExifTool Cleanup Thread</code>".
	 */
	private static final String CLEANUP_THREAD_NAME = "ExifTool Cleanup Thread";

	private static final String STREAM_CLOSED_MESSAGE = "Stream closed";
	static final String EXIF_DATE_FORMAT = "yyyy:MM:dd HH:mm:ss";

	private static final Logger log = LoggerFactory.getLogger(ExifTool.class);

	private final Map<Feature, Boolean> featureSupportedMap = new HashMap<Feature, Boolean>();
	private final Set<Feature> featureEnabledSet = EnumSet
			.noneOf(Feature.class);
	private final ReadOptions defReadOptions;
	private WriteOptions defWriteOptions = new WriteOptions();
	private final VersionNumber exifVersion;
	private final ExifProxy exifProxy;

	public ExifToolNew() {
		this((Feature[]) null);
	}

	/**
	 * In this constructor, exifToolPath and processCleanupDelay are read from
	 * system properties exiftool.path and exiftool.processCleanupDelay.
	 * processCleanupDelay is optional. If not found, the default is used.
	 */
	public ExifToolNew(Feature... features) {
		this(new ReadOptions(), features);
	}
	public ExifToolNew(long cleanupDelayInMillis, Feature... features) {
		this(new ReadOptions(), cleanupDelayInMillis, features);
	}
	public ExifToolNew(ReadOptions readOptions, Feature... features) {
		this(readOptions,Long.getLong(
				ENV_EXIF_TOOL_PROCESSCLEANUPDELAY,
				DEFAULT_PROCESS_CLEANUP_DELAY), features);
	}
	public ExifToolNew(ReadOptions readOptions, long cleanupDelayInMillis, Feature... features) {
		this(System.getProperty(ENV_EXIF_TOOL_PATH, "exiftool"), cleanupDelayInMillis, readOptions, features);
	}

	/**
	 * Pass in the absolute path to the ExifTool executable on the host system.
	 */
	public ExifToolNew(String exifToolPath) {
		this(exifToolPath, DEFAULT_PROCESS_CLEANUP_DELAY, new ReadOptions());
	}

	public ExifToolNew(String exifToolPath, Feature... features) {
		this(exifToolPath, DEFAULT_PROCESS_CLEANUP_DELAY, new ReadOptions(),
				features);
	}

	public ExifToolNew(String exifCmd, long processCleanupDelay,
			ReadOptions readOptions, Feature... features) {
		this.exifVersion = ExifProcess.readVersion(exifCmd);
		this.defReadOptions = readOptions;
		if (features != null && features.length > 0) {
			for (Feature feature : features) {
				if (!feature.isSupported(exifVersion)) {
					throw new UnsupportedFeatureException(feature);
				}
				this.featureEnabledSet.add(feature);
				this.featureSupportedMap.put(feature, true);
			}
		}

		List<String> baseArgs = new ArrayList<String>(3);
		if (featureEnabledSet.contains(Feature.MWG_MODULE)) {
			baseArgs.addAll(Arrays.asList("-use", "MWG"));
		}
		if (featureEnabledSet.contains(Feature.STAY_OPEN)) {
			KeepAliveExifProxy proxy = new KeepAliveExifProxy(exifCmd, baseArgs,processCleanupDelay);
			exifProxy = proxy;
		} else {
			if(processCleanupDelay!=0){
				throw new RuntimeException("The processCleanupDelay parameter should be 0 if no stay_open parameter is used. Was "+processCleanupDelay);
			}
			exifProxy = new SingleUseExifProxy(exifCmd, baseArgs);
		}
	}

	//
	// /**
	// * Limits the amount of time (in mills) an exif operation can take.
	// Setting
	// * value to greater than 0 to enable.
	// */
	// public ExifToolNew setRunTimeout(long mills) {
	// defReadOptions = defReadOptions.withRunTimeoutMills(mills);
	// defWriteOptions = defWriteOptions.withRunTimeoutMills(mills);
	// return this;
	// }

	/**
	 * Used to determine if the given {@link Feature} is supported by the
	 * underlying native install of ExifTool pointed at by
	 * {@link #ENV_EXIF_TOOL_PATH}.
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
	public boolean isFeatureSupported(Feature feature) throws RuntimeException {
		if (feature == null) {
			throw new IllegalArgumentException("feature cannot be null");
		}

		Boolean supported = featureSupportedMap.get(feature);

		/*
		 * If there is no Boolean flag for the feature, support for it hasn't
		 * been checked yet with the native ExifTool install, so we need to do
		 * that.
		 */
		if (supported == null) {
			log.debug("Support for feature %s has not been checked yet, checking...");
			supported = feature.isSupported(exifVersion);
			featureSupportedMap.put(feature, supported);
		}

		return supported;
	}

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
	public boolean isFeatureEnabled(Feature feature)
			throws IllegalArgumentException {
		if (feature == null) {
			throw new IllegalArgumentException("feature cannot be null");
		}
		return featureEnabledSet.contains(feature);
	}

	/**
	 * Used to startup the external ExifTool process and open the read/write
	 * streams used to communicate with it when {@link Feature#STAY_OPEN} is
	 * enabled. This method has no effect if the stay open feature is not
	 * enabled.
	 */
	public void startup() {
		exifProxy.startup();
	}

	/**
	 * This is same as {@link #close()}, added for consistency with
	 * {@link #startup()}
	 */
	public void shutdown() {
		close();
	}

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
	 * Calling this method on an instance of this class without
	 * {@link Feature#STAY_OPEN} support enabled has no effect.
	 */
	public void close() {
		exifProxy.shutdown();
	}

	public boolean isStayOpen() {
		return featureEnabledSet.contains(Feature.STAY_OPEN);
	}

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
	public boolean isRunning() {
		return exifProxy != null && !exifProxy.isRunning();
	}

	public ReadOptions getReadOptions() {
		return defReadOptions;
	}

	public WriteOptions getWriteOptions() {
		return defWriteOptions;
	}

	public ExifToolNew setWriteOptions(WriteOptions options) {
		defWriteOptions = options;
		return this;
	}

	public Map<MetadataTag, String> getImageMeta(File image, Tag... tags)
			throws IllegalArgumentException, SecurityException, IOException {
		return getImageMeta(image, Format.NUMERIC, tags);
	}

	public Map<MetadataTag, String> getImageMeta(File image, Format format,
			Tag... tags) throws IllegalArgumentException, SecurityException,
			IOException {

		String[] stringTags = new String[tags.length];
		int i = 0;
		for (Tag tag : tags) {
			stringTags[i++] = tag.getKey();
		}
		Map<String, String> result = getImageMeta(image, format, true,
				stringTags);
		return Tag.toTagMap(result);
	}

	public Map<String, String> getImageMeta(File image, Format format,
			TagGroup... tags) throws IllegalArgumentException,
			SecurityException, IOException {
		String[] stringTags = new String[tags.length];
		int i = 0;
		for (TagGroup tag : tags) {
			stringTags[i++] = tag.getKey();
		}
		return getImageMeta(image, format, false, stringTags);
	}

	public Map<String, String> getImageMeta(File file, Format format,
			boolean supressDuplicates, String... tags) throws IOException {
		ReadOptions options = defReadOptions
				.withNumericOutput(format == Format.NUMERIC)
				.withShowDuplicates(!supressDuplicates).withConvertTypes(false);
		Map<Object, Object> result = readMetadata(options, file, tags);
		Map<String, String> data = new TreeMap<String, String>();
		for (Map.Entry<Object, Object> entry : result.entrySet()) {
			data.put(entry.getKey().toString(),
					entry.getValue() != null ? entry.getValue().toString() : "");
		}
		return data;
	}

	public <T> void addImageMetadata(File image, Map<T, Object> values)
			throws IOException {
		writeMetadata(defWriteOptions.withDeleteBackupFile(false), image,
				values);
	}

	// ================================================================================
	public Map<Object, Object> readMetadata(File file, Object... tags)
			throws IOException {
		return readMetadata(defReadOptions, file, tags);
	}

	public Map<Object, Object> readMetadata(ReadOptions options,
			File file, Object... tags) throws IOException {
		if (file == null) {
			throw new IllegalArgumentException(
					"file cannot be null and must be a valid stream of image data.");
		}
		if (!file.canRead()) {
			throw new SecurityException(
					"Unable to read the given image ["
							+ file.getAbsolutePath()
							+ "], ensure that the image exists at the given path and that the executing Java process has permissions to read it.");
		}

		List<String> args = new ArrayList<String>(tags.length + 2);
		if (options.numericOutput) {
			args.add("-n"); // numeric output
		}
		if (options.showDuplicates) {
			args.add("-a");
		}
		if (!options.showEmptyTags) {
			args.add("-S"); // compact output
		}
		for (Object tag : tags) {
			if (tag instanceof MetadataTag) {
				args.add("-" + ((MetadataTag) tag).getKey());
			} else {
				args.add("-" + tag);
			}
		}
		args.add(file.getAbsolutePath());

		Map<String, String> resultMap = exifProxy.execute(
				options.runTimeoutMills, args);

		Map<Object, Object> metadata = new HashMap<Object, Object>(
				resultMap.size());

		for (Object tag : tags) {
			MetadataTag metaTag;
			if (tag instanceof MetadataTag) {
				metaTag = (MetadataTag) tag;
			} else {
				metaTag = toTag(tag.toString());
			}
			if (metaTag.isMapped()) {
				String input = resultMap.remove(metaTag.getKey());
				if (!options.showEmptyTags
						&& (input == null || input.isEmpty())) {
					continue;
				}
				Object value = options.convertTypes ? Tag.deserialize(
						metaTag.getKey(), input, metaTag.getType()) : input;
				// maps with tag passed in, as caller expects to fetch
				metadata.put(metaTag, value);
			}
		}
		for (Map.Entry<String, String> entry : resultMap.entrySet()) {
			if (!options.showEmptyTags && entry.getValue() == null
					|| entry.getValue().isEmpty()) {
				continue;
			}
			if (options.convertTypes) {
				MetadataTag metaTag = toTag(entry.getKey());
				Object value = Tag.deserialize(metaTag.getKey(),
						entry.getValue(), metaTag.getType());
				metadata.put(entry.getKey(), value);
			} else {
				metadata.put(entry.getKey(), entry.getValue());

			}
		}
		return metadata;
	}

	public <T> void writeMetadata(File image, Map<T, Object> values)
			throws IOException {
		writeMetadata(defWriteOptions, image, values);
	}

	/**
	 * Takes a map of tags (either (@link Tag) or Strings for keys) and
	 * replaces/appends them to the metadata.
	 */
	public <T> void writeMetadata(WriteOptions options, File image,
			Map<T, Object> values) throws IOException {
		if (image == null) {
			throw new IllegalArgumentException(
					"image cannot be null and must be a valid stream of image data.");
		}
		if (!image.exists())
			throw new FileNotFoundException(String.format(
					"File \"%s\" does not exits", image.getAbsolutePath()));
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException(
					"values cannot be null and must contain 1 or more tag to value mappings");
		}

		if (!image.canWrite()) {
			throw new SecurityException(
					"Unable to write the given image ["
							+ image.getAbsolutePath()
							+ "], ensure that the image exists at the given path and that the executing Java process has permissions to write to it.");
		}
		log.info("Adding Tags {} to {}", values, image.getAbsolutePath());

		// start process
		long startTime = System.currentTimeMillis();
		execute(options, image, values);

		// Print out how long the call to external ExifTool process took.
		if (log.isDebugEnabled()) {
			log.debug(String.format(
					"Image Meta Processed in %d ms [added %d tags]",
					(System.currentTimeMillis() - startTime), values.size()));
		}
	}

	private <T> void execute(WriteOptions options, File image,
			Map<T, Object> values) throws IOException {
		List<String> args = new ArrayList<String>(values.size() + 3);
		for (Map.Entry<?, Object> entry : values.entrySet()) {
			args.addAll(serializeToArgs(entry.getKey(), entry.getValue()));
		}
		args.add(image.getAbsolutePath());

		try {
			exifProxy.execute(options.runTimeoutMills, args);
		} finally {
			if (options.deleteBackupFile) {
				File origBackup = new File(image.getAbsolutePath()
						+ "_original");
				if (origBackup.exists())
					origBackup.delete();
			}
		}
	}

	public void rebuildMetadata(File file) throws IOException {
		rebuildMetadata(getWriteOptions(), file);
	}

	/**
	 * Rewrite all the the metadata tags in a JPEG image. This will not work for
	 * TIFF files. Use this when the image has some corrupt tags.
	 * 
	 * @link http://www.sno.phy.queensu.ca/~phil/exiftool/faq.html#Q20
	 */
	public void rebuildMetadata(WriteOptions options, File file)
			throws IOException {
		if (file == null)
			throw new NullPointerException("File is null");
		if (!file.exists())
			throw new FileNotFoundException(String.format(
					"File \"%s\" does not exits", file.getAbsolutePath()));
		if (!file.canWrite())
			throw new SecurityException(String.format(
					"File \"%s\" cannot be written to", file.getAbsolutePath()));

		List<String> args = Arrays.asList("-all=", "-tagsfromfile", "@",
				"-all:all", "-unsafe", file.getAbsolutePath());
		try {
			exifProxy.execute(options.runTimeoutMills, args);
		} finally {
			if (options.deleteBackupFile) {
				File origBackup = new File(file.getAbsolutePath() + "_original");
				if (origBackup.exists())
					origBackup.delete();
			}
		}
	}

	// ================================================================================
	// STATIC helpers

	static List<String> serializeToArgs(Object tag, Object value) {
		final Class tagType;
		final String tagName;
		if (tag instanceof MetadataTag) {
			tagName = ((MetadataTag) tag).getKey();
			tagType = ((MetadataTag) tag).getType();
		} else {
			tagName = tag.toString();
			tagType = null;
		}

		// pre process
		if (value != null) {
			if (value.getClass().isArray()) {
				// convert array to iterable, this is lame
				int len = Array.getLength(value);
				List<Object> newList = new ArrayList<Object>(len);
				for (int i = 0; i < len; i++) {
					Object item = Array.get(value, i);
					newList.add(item);
				}
				value = newList;
			} else if (value instanceof Number && Date.class.equals(tagType)) {
				// if we know this is a date field and data is a number assume
				// it is unix epoch time
				Date date = new Date(((Number) value).longValue());
				value = date;
			}
		}

		List<String> args = new ArrayList<String>(4);
		String arg;
		if (value == null) {
			arg = String.format("-%s=", tagName);
		} else if (value instanceof Number) {
			arg = String.format("-%s#=%s", tagName, value);
		} else if (value instanceof Date) {
			SimpleDateFormat formatter = new SimpleDateFormat(EXIF_DATE_FORMAT);
			arg = String.format("-%s=%s", tagName,
					formatter.format((Date) value));
		} else if (value instanceof Iterable) {
			Iterable it = (Iterable) value;
			args.add("-sep");
			args.add(",");
			StringBuilder itemList = new StringBuilder();
			for (Object item : it) {
				if (itemList.length() > 0) {
					itemList.append(",");
				}
				itemList.append(item);
			}
			arg = String.format("-%s=%s", tagName, itemList);
		} else {
			if (tagType != null && tagType.isArray()) {
				args.add("-sep");
				args.add(",");
			}
			arg = String.format("-%s=%s", tagName, value);
		}
		args.add(arg);
		return args;
	}

	static MetadataTag toTag(String name) {
		for (Tag tag : Tag.values()) {
			if (tag.getKey().equalsIgnoreCase(name)) {
				return tag;
			}
		}
		for (MwgTag tag : MwgTag.values()) {
			if (tag.getKey().equalsIgnoreCase(name)) {
				return tag;
			}
		}
		return new CustomTag(name, String.class);
	}

	@Override
	public String getImageMetadataXml(File input, boolean includeBinary)
			throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public void getImageMetadataXml(File input, File output,
			boolean includeBinary) throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public String extractImageIccProfile(File input, File output)
			throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public File extractThumbnail(File input, Tag tag) throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public Map<MetadataTag, String> getImageMeta(File image,
			MetadataTag... tags) throws IllegalArgumentException,
			SecurityException, IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public Map<Object, Object> getImageMeta2(File file, MetadataTag... tags)
			throws IllegalArgumentException, SecurityException, IOException {
		return readMetadata(file, tags);
	}

	@Override
	public Map<MetadataTag, String> getImageMeta(File file, Format format,
			MetadataTag... tags) throws IllegalArgumentException,
			SecurityException, IOException {
		Map<?,?> result = readMetadata(file, tags);
		//since meta tags are passed we will have a proper Map result 
		return (Map)result;
	}
	@Override
	protected void finalize() throws Throwable {
		log.info("ExifTool not used anymore shutdown the exiftool process...");
		shutdown();
		super.finalize();
	}
}