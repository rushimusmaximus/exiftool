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

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import com.thebuzzmedia.exiftool.adapters.ExifToolService;

/**
 * Class used to provide a Java-like interface to Phil Harvey's excellent, Perl-based <a
 * href="http://www.sno.phy.queensu.ca/~phil/exiftool">ExifToolNew3</a>.
 * <p/>
 * There are a number of other basic Java wrappers to ExifToolNew3 available online, but most of them only abstract out
 * the actual Java-external-process execution logic and do no additional work to make integration with the external
 * ExifToolNew3 any easier or intuitive from the perspective of the Java application written to make use of
 * ExifToolNew3.
 * <p/>
 * This class was written in order to make integration with ExifToolNew3 inside of a Java application seamless and
 * performant with the goal being that the developer can treat ExifToolNew3 as if it were written in Java, garnering all
 * of the benefits with none of the added headache of managing an external native process from Java.
 * <p/>
 * Phil Harvey's ExifToolNew3 is written in Perl and runs on all major platforms (including Windows) so no portability
 * issues are introduced into your application by utilizing this class.
 * <h3>Usage</h3>
 * Assuming ExifToolNew3 is installed on the host system correctly and either in the system path or pointed to by
 * {@link #EXIF_TOOL_PATH}, using this class to communicate with ExifToolNew3 is as simple as creating an instance (
 * <code>ExifToolNew3 tool = new ExifToolNew3()</code>) and then making calls to
 * {@link #getImageMeta3(File, ReadOptions, Tag...)} or {@link #getImageMeta4(File, ReadOptions, Format, Tag...)} with a
 * list of {@link Tag}s you want to pull values for from the given image.
 * <p/>
 * In this default mode, calls to <code>getImageMeta</code> will automatically start an external ExifToolNew3 process to
 * handle the request. After ExifToolNew3 has parsed the tag values from the file, the external process exits and this
 * class parses the result before returning it to the caller.
 * <p/>
 * Results from calls to <code>getImageMeta</code> are returned in a {@link Map} with the {@link Tag} values as the keys
 * and {@link String} values for every tag that had a value in the image file as the values. {@link Tag}s with no value
 * found in the image are omitted from the result map.
 * <p/>
 * While each {@link Tag} provides a hint at which format the resulting value for that tag is returned as from
 * ExifToolNew3 (see {@link Tag#getType()}), that only applies to values returned with an output format of
 * {@link Format#NUMERIC} and it is ultimately up to the caller to decide how best to parse or convert the returned
 * values.
 * <p/>
 * The {@link Tag} Enum provides the {@link Tag#parseValue(Tag, String)} convenience method for parsing given
 * <code>String</code> values according to the Tag hint automatically for you if that is what you plan on doing,
 * otherwise feel free to handle the return values anyway you want.
 * <h3>ExifToolNew3 -stay_open Support</h3>
 * ExifToolNew3 <a href= "http://u88.n24.queensu.ca/exiftool/forum/index.php/topic,1402.msg12933.html#msg12933"
 * >8.36</a> added a new persistent-process feature that allows ExifToolNew3 to stay running in a daemon mode and
 * continue accepting commands via a file or stdin.
 * <p/>
 * This new mode is controlled via the <code>-stay_open True/False</code> command line argument and in a busy system
 * that is making thousands of calls to ExifToolNew3, can offer speed improvements of up to <strong>60x</strong> (yes,
 * really that much).
 * <p/>
 * This feature was added to ExifToolNew3 shortly after user <a href="http://www.christian-etter.de/?p=458">Christian
 * Etter discovered</a> the overhead for starting up a new Perl interpreter each time ExifToolNew3 is loaded accounts
 * for roughly <a href= "http://u88.n24.queensu.ca/exiftool/forum/index.php/topic,1402.msg6121.html#msg6121" >98.4% of
 * the total runtime</a>.
 * <p/>
 * Support for using ExifToolNew3 in daemon mode is enabled by passing {@link Feature#STAY_OPEN} to the constructor of
 * the class when creating an instance of this class and then simply using the class as you normally would. This class
 * will manage a single ExifToolNew3 process running in daemon mode in the background to service all future calls to the
 * class.
 * <p/>
 * Because this feature requires ExifToolNew3 8.36 or later, this class will actually verify support for the feature in
 * the version of ExifToolNew3 pointed at by {@link #EXIF_TOOL_PATH} before successfully instantiating the class and
 * will notify you via an {@link UnsupportedFeatureException} if the native ExifToolNew3 doesn't support the requested
 * feature.
 * <p/>
 * In the event of an {@link UnsupportedFeatureException}, the caller can either upgrade the native ExifToolNew3 upgrade
 * to the version required or simply avoid using that feature to work around the exception.
 * <h3>Automatic Resource Cleanup</h3>
 * When {@link Feature#STAY_OPEN} mode is used, there is the potential for leaking both host OS processes (native
 * 'exiftool' processes) as well as the read/write streams used to communicate with it unless {@link #close()} is called
 * to clean them up when done. <strong>Fortunately</strong>, this class provides an automatic cleanup mechanism that
 * runs, by default, after 10mins of inactivity to clean up those stray resources.
 * <p/>
 * The inactivity period can be controlled by modifying the {@link #PROCESS_CLEANUP_DELAY} system variable. A value of
 * <code>0</code> or less disabled the automatic cleanup process and requires you to cleanup ExifToolNew3 instances on
 * your own by calling {@link #close()} manually.
 * <p/>
 * Any class activity by way of calls to <code>getImageMeta</code> will always reset the inactivity timer, so in a busy
 * system the cleanup thread could potentially never run, leaving the original host ExifToolNew3 process running forever
 * (which is fine).
 * <p/>
 * This design was chosen to help make using the class and not introducing memory leaks and bugs into your code easier
 * as well as making very inactive instances of this class light weight while not in-use by cleaning up after
 * themselves.
 * <p/>
 * The only overhead incurred when opening the process back up is a 250-500ms lag while launching the VM interpreter
 * again on the first call (depending on host machine speed and load).
 * <h3>Reusing a "closed" ExifToolNew3 Instance</h3>
 * If you or the cleanup thread have called {@link #close()} on an instance of this class, cleaning up the host process
 * and read/write streams, the instance of this class can still be safely used. Any followup calls to
 * <code>getImageMeta</code> will simply re-instantiate all the required resources necessary to service the call
 * (honoring any {@link Feature}s set).
 * <p/>
 * This can be handy behavior to be aware of when writing scheduled processing jobs that may wake up every hour and
 * process thousands of pictures then go back to sleep. In order for the process to execute as fast as possible, you
 * would want to use ExifToolNew3 in daemon mode (pass {@link Feature#STAY_OPEN} to the constructor of this class) and
 * when done, instead of {@link #close()}-ing the instance of this class and throwing it out, you can keep the reference
 * around and re-use it again when the job executes again an hour later.
 * <h3>Performance</h3>
 * Extra care is taken to ensure minimal object creation or unnecessary CPU overhead while communicating with the
 * external process.
 * <p/>
 * {@link Pattern}s used to split the responses from the process are explicitly compiled and reused, string
 * concatenation is minimized, Tag name lookup is done via a <code>static final</code> {@link Map} shared by all
 * instances and so on.
 * <p/>
 * Additionally, extra care is taken to utilize the most optimal code paths when initiating and using the external
 * process, for example, the {@link ProcessBuilder#command(List)} method is used to avoid the copying of array elements
 * when {@link ProcessBuilder#command(String...)} is used and avoiding the (hidden) use of {@link StringTokenizer} when
 * {@link Runtime#exec(String)} is called.
 * <p/>
 * All of this effort was done to ensure that imgscalr and its supporting classes continue to provide best-of-breed
 * performance and memory utilization in long running/high performance environments (e.g. web applications).
 * <h3>Thread Safety</h3>
 * Instances of this class are <strong>not</strong> Thread-safe. Both the instance of this class and external
 * ExifToolNew3 process maintain state specific to the current operation. Use of instances of this class need to be
 * synchronized using an external mechanism or in a highly threaded environment (e.g. web application), instances of
 * this class can be used along with {@link ThreadLocal}s to ensure Thread-safe, highly parallel use.
 * <h3>Why ExifToolNew3?</h3>
 * <a href="http://www.sno.phy.queensu.ca/~phil/exiftool">ExifToolNew3</a> is written in Perl and requires an external
 * process call from Java to make use of.
 * <p/>
 * While this would normally preclude a piece of software from inclusion into the imgscalr library (more complex
 * integration), there is no other image metadata piece of software available as robust, complete and well-tested as
 * ExifToolNew3. In addition, ExifToolNew3 already runs on all major platforms (including Windows), so there was not a
 * lack of portability introduced by providing an integration for it.
 * <p/>
 * Allowing it to be used from Java is a boon to any Java project that needs the ability to read/write image-metadata
 * from almost <a href="http://www.sno.phy.queensu.ca/~phil/exiftool/#supported">any image or video file</a> format.
 * <h3>Alternatives</h3>
 * If integration with an external Perl process is something your app cannot do and you still need image
 * metadata-extraction capability, Drew Noakes has written the 2nd most robust image metadata library I have come
 * across: <a href="http://drewnoakes.com/drewnoakes.com/code/exif/">Metadata Extractor</a> that you might want to look
 * at.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
public class ExifToolNew2 implements RawExifTool {
	/**
	 * Flag used to indicate if debugging output has been enabled by setting the "<code>exiftool.debug</code>" system
	 * property to <code>true</code>. This value will be <code>false</code> if the " <code>exiftool.debug</code>" system
	 * property is undefined or set to <code>false</code>.
	 * <p/>
	 * This system property can be set on startup with:<br/>
	 * <code>
	 * -Dexiftool.debug=true
	 * </code> or by calling {@link System#setProperty(String, String)} before this class is loaded.
	 * <p/>
	 * Default value is <code>false</code>.
	 */
	public static final Boolean DEBUG = Boolean.getBoolean("exiftool.debug");

	/**
	 * Prefix to every log message this library logs. Using a well-defined prefix helps make it easier both visually and
	 * programmatically to scan log files for messages produced by this library.
	 * <p/>
	 * The value is "<code>[exiftool] </code>" (including the space).
	 */
	public static final String LOG_PREFIX = "[exiftool] ";

	/**
	 * The absolute path to the ExifToolNew3 executable on the host system running this class as defined by the "
	 * <code>exiftool.path</code>" system property.
	 * <p/>
	 * If ExifToolNew3 is on your system path and running the command "exiftool" successfully executes it, leaving this
	 * value unchanged will work fine on any platform. If the ExifToolNew3 executable is named something else or not in
	 * the system path, then this property will need to be set to point at it before using this class.
	 * <p/>
	 * This system property can be set on startup with:<br/>
	 * <code>
	 * -Dexiftool.path=/path/to/exiftool
	 * </code> or by calling {@link System#setProperty(String, String)} before this class is loaded.
	 * <p/>
	 * On Windows be sure to double-escape the path to the tool, for example: <code>
	 * -Dexiftool.path=C:\\Tools\\exiftool.exe
	 * </code>
	 * <p/>
	 * Default value is "<code>exiftool</code>".
	 * <h3>Relative Paths</h3>
	 * Relative path values (e.g. "bin/tools/exiftool") are executed with relation to the base directory the VM process
	 * was started in. Essentially the directory that <code>new File(".").getAbsolutePath()</code> points at during
	 * runtime.
	 */
	public static final String EXIF_TOOL_PATH = System.getProperty("exiftool.path", "exiftool");

	/**
	 * Interval (in milliseconds) of inactivity before the cleanup thread wakes up and cleans up the daemon ExifToolNew3
	 * process and the read/write streams used to communicate with it when the {@link Feature#STAY_OPEN} feature is
	 * used.
	 * <p/>
	 * Ever time a call to <code>getImageMeta</code> is processed, the timer keeping track of cleanup is reset; more
	 * specifically, this class has to experience no activity for this duration of time before the cleanup process is
	 * fired up and cleans up the host OS process and the stream resources.
	 * <p/>
	 * Any subsequent calls to <code>getImageMeta</code> after a cleanup simply re-initializes the resources.
	 * <p/>
	 * This system property can be set on startup with:<br/>
	 * <code>
	 * -Dexiftool.processCleanupDelay=600000
	 * </code> or by calling {@link System#setProperty(String, String)} before this class is loaded.
	 * <p/>
	 * Setting this value to 0 disables the automatic cleanup thread completely and the caller will need to manually
	 * cleanup the external ExifToolNew3 process and read/write streams by calling {@link #close()}.
	 * <p/>
	 * Default value is <code>600,000</code> (10 minutes).
	 */
	public static final long PROCESS_CLEANUP_DELAY = Long.getLong("exiftool.processCleanupDelay", 600000);

	/**
	 * Name used to identify the (optional) cleanup {@link Thread}.
	 * <p/>
	 * This is only provided to make debugging and profiling easier for implementors making use of this class such that
	 * the resources this class creates and uses (i.e. Threads) are readily identifiable in a running VM.
	 * <p/>
	 * Default value is "<code>ExifToolNew3 Cleanup Thread</code>".
	 */
	protected static final String CLEANUP_THREAD_NAME = "ExifToolNew3 Cleanup Thread";

	/**
	 * Compiled {@link Pattern} of ": " used to split compact output from ExifToolNew3 evenly into name/value pairs.
	 */
	protected static final Pattern TAG_VALUE_PATTERN = Pattern.compile(": ");

	/**
	 * Map shared across all instances of this class that maintains the state of {@link Feature}s and if they are
	 * supported or not (supported=true, unsupported=false) by the underlying native ExifToolNew3 process being used in
	 * conjunction with this class.
	 * <p/>
	 * If a {@link Feature} is missing from the map (has no <code>true</code> or <code>false</code> flag associated with
	 * it, but <code>null</code> instead) then that means that feature has not been checked for support yet and this
	 * class will know to call {@link #checkFeatureSupport(Feature...)} on it to determine its supported state.
	 * <p/>
	 * For efficiency reasons, individual {@link Feature}s are checked for support one time during each run of the VM
	 * and never again during the session of that running VM.
	 */
	protected static final Map<Feature, Boolean> FEATURE_SUPPORT_MAP = new HashMap<Feature, Boolean>();

	/**
	 * Static list of args used to execute ExifToolNew3 using the '-ver' flag in order to get it to print out its
	 * version number. Used by the {@link #checkFeatureSupport(Feature...)} method to check all the required feature
	 * versions.
	 * <p/>
	 * Defined here as a <code>static final</code> list because it is used every time and never changes.
	 */
	private static final List<String> VERIFY_FEATURE_ARGS = new ArrayList<String>(2);

	static {
		VERIFY_FEATURE_ARGS.add(EXIF_TOOL_PATH);
		VERIFY_FEATURE_ARGS.add("-ver");
	}

	/**
	 * Used to determine if the given {@link Feature} is supported by the underlying native install of ExifToolNew3
	 * pointed at by {@link #EXIF_TOOL_PATH}.
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

	@Override
	public boolean isFeatureSupported(Feature feature) throws IllegalArgumentException, RuntimeException {
		if (feature == null)
			throw new IllegalArgumentException("feature cannot be null");

		Boolean supported = FEATURE_SUPPORT_MAP.get(feature);

		/*
		 * If there is no Boolean flag for the feature, support for it hasn't been checked yet with the native
		 * ExifToolNew3 install, so we need to do that.
		 */
		if (supported == null) {
			log("\tSupport for feature %s has not been checked yet, checking...");
			checkFeatureSupport(feature);

			// Re-query for the supported state
			supported = FEATURE_SUPPORT_MAP.get(feature);
		}

		return supported;
	}

	/**
	 * Helper method used to ensure a message is loggable before it is logged and then pre-pend a universal prefix to
	 * all log messages generated by this library to make the log entries easy to parse visually or programmatically.
	 * <p/>
	 * If a message cannot be logged (logging is disabled) then this method returns immediately.
	 * <p/>
	 * <strong>NOTE</strong>: Because Java will auto-box primitive arguments into Objects when building out the
	 * <code>params</code> array, care should be taken not to call this method with primitive values unless
	 * {@link #DEBUG} is <code>true</code>; otherwise the VM will be spending time performing unnecessary auto-boxing
	 * calculations.
	 * 
	 * @param message
	 *            The log message in <a href=
	 *            "http://download.oracle.com/javase/6/docs/api/java/util/Formatter.html#syntax" >format string
	 *            syntax</a> that will be logged.
	 * @param params
	 *            The parameters that will be swapped into all the place holders in the original messages before being
	 *            logged.
	 * 
	 * @see #LOG_PREFIX
	 */
	protected static void log(String message, Object... params) {
		if (DEBUG)
			ExifToolNew3.log.debug(LOG_PREFIX + message + '\n', params);
	}

	/**
	 * Used to verify the version of ExifToolNew3 installed is a high enough version to support the given features.
	 * <p/>
	 * This method runs the command "<code>exiftool -ver</code>" to get the version of the installed ExifToolNew3 and
	 * then compares that version to the least required version specified by the given features (see
	 * {@link Feature#getVersion()}).
	 * 
	 * @param features
	 *            The features whose required versions will be checked against the installed ExifToolNew3 for support.
	 * 
	 * @throws RuntimeException
	 *             if any exception occurs communicating with the external ExifToolNew3 process spun up in order to
	 *             check its version.
	 */
	protected static void checkFeatureSupport(Feature... features) throws RuntimeException {
		// Ensure there is work to do.
		if (features == null || features.length == 0)
			return;

		log("\tChecking %d feature(s) for support in the external ExifToolNew3 install...", features.length);

		for (int i = 0; i < features.length; i++) {
			String ver = null;
			Boolean supported;
			Feature feature = features[i];

			log("\t\tChecking feature %s for support, requires ExifToolNew3 version %s or higher...", feature,
					feature.getVersion());

			// Execute 'exiftool -ver'
			IOStream streams = startExifToolProcess(VERIFY_FEATURE_ARGS);

			try {
				// Read the single-line reply (version number)
				ver = streams.reader.readLine();
			} catch (Exception e) {
				/*
				 * no-op, while it is important to know that we COULD launch the ExifToolNew3 process (i.e.
				 * startExifToolProcess call worked) but couldn't communicate with it, the context with which this
				 * method is called is from the constructor of this class which would just wrap this exception and
				 * discard it anyway if it failed.
				 * 
				 * the caller will realize there is something wrong with the ExifToolNew3 process communication as soon
				 * as they make their first call to getImageMeta in which case whatever was causing the exception here
				 * will popup there and then need to be corrected.
				 * 
				 * This is an edge case that should only happen in really rare scenarios, so making this method easier
				 * to use is more important that robust IOException handling right here.
				 */
			} finally {
				// Close r/w streams to exited process.
				streams.close();
			}

			// Ensure the version found is >= the required version.
			if (ver != null && ver.compareTo(feature.getVersion().toString()) >= 0) {
				supported = Boolean.TRUE;
				log("\t\tFound ExifToolNew3 version %s, feature %s is SUPPORTED.", ver, feature);
			} else {
				supported = Boolean.FALSE;
				log("\t\tFound ExifToolNew3 version %s, feature %s is NOT SUPPORTED.", ver, feature);
			}

			// Update feature support map
			FEATURE_SUPPORT_MAP.put(feature, supported);
		}
	}

	protected static IOStream startExifToolProcess(List<String> args) throws RuntimeException {
		Process proc = null;
		IOStream streams = null;

		log("\tAttempting to start external ExifToolNew3 process using args: %s", args);

		try {
			proc = new ProcessBuilder(args).start();
			log("\t\tSuccessful");
		} catch (Exception e) {
			String message = "Unable to start external ExifToolNew3 process using the execution arguments: " + args
					+ ". Ensure ExifToolNew3 is installed correctly and runs using the command path '" + EXIF_TOOL_PATH
					+ "' as specified by the 'exiftool.path' system property.";

			log(message);
			throw new RuntimeException(message, e);
		}

		log("\tSetting up Read/Write streams to the external ExifToolNew3 process...");

		// Setup read/write streams to the new process.
		streams = new IOStream(new BufferedReader(new InputStreamReader(proc.getInputStream())),
				new OutputStreamWriter(proc.getOutputStream()));

		log("\t\tSuccessful, returning streams to caller.");
		return streams;
	}

	private Timer cleanupTimer;
	private TimerTask currentCleanupTask;

	IOStream streams;
	List<String> args;

	Set<Feature> featureSet;

	public ExifToolNew2() {
		this((Feature[]) null);
	}

	public ExifToolNew2(Feature... features) throws UnsupportedFeatureException {
		featureSet = new HashSet<Feature>();

		if (features != null && features.length > 0) {
			/*
			 * Process all features to ensure we checked them for support in the installed version of ExifToolNew3. If
			 * the feature has already been checked before, this method will return immediately.
			 */
			checkFeatureSupport(features);

			/*
			 * Now we need to verify that all the features requested for this instance of ExifToolNew3 to use WERE
			 * supported after all.
			 */
			for (int i = 0; i < features.length; i++) {
				Feature f = features[i];

				/*
				 * If the Feature was supported, record it in the local featureSet so this instance knows what features
				 * are being turned on by the caller.
				 * 
				 * If the Feature was not supported, throw an exception reporting it to the caller so they know it
				 * cannot be used.
				 */
				if (FEATURE_SUPPORT_MAP.get(f).booleanValue())
					featureSet.add(f);
				else
					throw new UnsupportedFeatureException(f);
			}
		}

		args = new ArrayList<String>(64);

		/*
		 * Now that initialization is done, init the cleanup timer if we are using STAY_OPEN and the delay time set is
		 * non-zero.
		 */
		if (isFeatureEnabled(Feature.STAY_OPEN) && PROCESS_CLEANUP_DELAY > 0) {
			this.cleanupTimer = new Timer(CLEANUP_THREAD_NAME, true);

			// Start the first cleanup task counting down.
			resetCleanupTask();
		}
	}

	public void shutdownCleanupTask() {
		if (currentCleanupTask != null) {
			currentCleanupTask.cancel();
		}
		currentCleanupTask = null;
		if (cleanupTimer != null) {
			cleanupTimer.cancel();
		}
	}

	/**
	 * Used to shutdown the external ExifToolNew3 process and close the read/write streams used to communicate with it
	 * when {@link Feature#STAY_OPEN} is enabled.
	 * <p/>
	 * <strong>NOTE</strong>: Calling this method does not preclude this instance of {@link ExifToolNew3} from being
	 * re-used, it merely disposes of the native and internal resources until the next call to <code>getImageMeta</code>
	 * causes them to be re-instantiated.
	 * <p/>
	 * The cleanup thread will automatically call this after an interval of inactivity defined by
	 * {@link #PROCESS_CLEANUP_DELAY}.
	 * <p/>
	 * Calling this method on an instance of this class without {@link Feature#STAY_OPEN} support enabled has no effect.
	 */
	public void close() {
		/*
		 * no-op if the underlying process and streams have already been closed OR if stayOpen was never used in the
		 * first place in which case nothing is open right now anyway.
		 */
		if (streams == null)
			return;

		/*
		 * If ExifToolNew3 was used in stayOpen mode but getImageMeta was never called then the streams were never
		 * initialized and there is nothing to shut down or destroy, otherwise we need to close down all the resources
		 * in use.
		 */
		if (streams == null) {
			log("\tThis ExifToolNew3 instance was never used so no external process or streams were ever created (nothing to clean up, we will just exit).");
		} else {
			try {
				log("\tAttempting to close ExifToolNew3 daemon process, issuing '-stay_open\\nFalse\\n' command...");

				// Tell the ExifToolNew3 process to exit.
				streams.writer.write("-stay_open\nFalse\n");
				streams.writer.flush();

				log("\t\tSuccessful");
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				streams.close();
			}
		}

		streams = null;
		log("\tExifTool daemon process successfully terminated.");
	}

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
	public boolean isRunning() {
		return (streams != null);
	}

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
	public boolean isFeatureEnabled(Feature feature) throws IllegalArgumentException {
		if (feature == null)
			throw new IllegalArgumentException("feature cannot be null");

		return featureSet.contains(feature);
	}
	@Override
	public Map<String, String> getImageMeta(File image, ReadOptions options, String... tags)
			throws IllegalArgumentException, SecurityException, IOException {
		return ExifToolService.toMap(execute3(image,options,tags));
	}
	private List<String> execute3(File image, ReadOptions options, String... tags) {
		try {
			return execute2(image,options,tags);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	private List<String> execute2(File image, ReadOptions options, String... tags) throws IOException {
		if (image == null)
			throw new IllegalArgumentException("image cannot be null and must be a valid stream of image data.");
		if (options == null)
			throw new IllegalArgumentException("options cannot be null");
		// if (tags == null) {
		// tags = new MetadataTag[0];
		// }
		if (tags == null || tags.length == 0)
			throw new IllegalArgumentException(
					"tags cannot be null and must contain 1 or more Tag to query the image for.");
		if (!image.canRead())
			throw new SecurityException(
					"Unable to read the given image ["
							+ image.getAbsolutePath()
							+ "], ensure that the image exists at the given path and that the executing Java process has permissions to read it.");

		long startTime = System.currentTimeMillis();

		if (DEBUG)
			log("Querying %d tags from image: %s", tags.length, image.getAbsolutePath());

		long exifToolCallElapsedTime = 0;

		/*
		 * Using ExifToolNew3 in daemon mode (-stay_open True) executes different code paths below. So establish the
		 * flag for this once and it is reused a multitude of times later in this method to figure out where to branch
		 * to.
		 */
		boolean stayOpen = isStayOpen();

		// Clear process args
		args.clear();

		if (stayOpen) {
			log("\tUsing ExifToolNew3 in daemon mode (-stay_open True)...");

			// Always reset the cleanup task.
			resetCleanupTask();

			/*
			 * If this is our first time calling getImageMeta with a stayOpen connection, set up the persistent process
			 * and run it so it is ready to receive commands from us.
			 */
			if (streams == null) {
				log("\tStarting daemon ExifToolNew3 process and creating read/write streams (this only happens once)...");

				args.add(EXIF_TOOL_PATH);
				args.add("-stay_open");
				args.add("True");
				args.add("-@");
				args.add("-");

				// Begin the persistent ExifToolNew3 process.
				streams = startExifToolProcess(args);
			}

			log("\tStreaming arguments to ExifToolNew3 process...");

			if (options.numericOutput)
				streams.writer.write("-n\n"); // numeric output

			streams.writer.write("-S\n"); // compact output

			for (int i = 0; i < tags.length; i++) {
				streams.writer.write('-');
				streams.writer.write(tags[i]);
				streams.writer.write("\n");
			}

			streams.writer.write(image.getAbsolutePath());
			streams.writer.write("\n");

			log("\tExecuting ExifToolNew3...");
			// Run ExifToolNew3 on our file with all the given arguments.
			streams.writer.write("-execute\n");
			streams.writer.flush();
		} else {
			log("\tUsing ExifToolNew3 in non-daemon mode (-stay_open False)...");

			/*
			 * Since we are not using a stayOpen process, we need to setup the execution arguments completely each time.
			 */
			args.add(EXIF_TOOL_PATH);

			if (options.numericOutput)
				args.add("-n"); // numeric output

			args.add("-S"); // compact output

			for (int i = 0; i < tags.length; i++)
				args.add("-" + tags[i]);

			args.add(image.getAbsolutePath());

			// Run the ExifToolNew3 with our args.
			streams = startExifToolProcess(args);

		}

		return readResponse(startTime);
	}

	private List<String> readResponse(long startTime)
			throws IOException {
		boolean stayOpen = isStayOpen();

		// Begin tracking the duration ExifToolNew3 takes to respond.
		long exifToolCallElapsedTime = System.currentTimeMillis();
		log("\tReading response back from ExifToolNew3...");

		String line = null;
		/*
		 * Create a result map big enough to hold results for each of the tags and avoid collisions while inserting.
		 */
		List<String> resultMap = new ArrayList<String>();

		while ((line = streams.reader.readLine()) != null) {
			resultMap.add(line);
//			String[] pair = TAG_VALUE_PATTERN.split(line);
//
//			if (pair != null && pair.length == 2) {
//				// Determine the tag represented by this value.
//				resultMap.put(pair[0], pair[1]);
//				// Tag tag = Tag.forName(pair[0]);
//
//				/*
//				 * Store the tag and the associated value in the result map only if we were able to map the name back to
//				 * a Tag instance. If not, then this is an unknown/unexpected tag return value and we skip it since we
//				 * cannot translate it back to one of our supported tags.
//				 */
//				// if (tag != null) {
//				// resultMap.put(tag, pair[1]);
//				log("\t\tRead Tag [name=%s, value=%s]", pair[0], pair[1]);// tag.getKey(),
//				// pair[1]);
//				// }
//			}

			/*
			 * When using a persistent ExifToolNew3 process, it terminates its output to us with a "{ready}" clause on a
			 * new line, we need to look for it and break from this loop when we see it otherwise this process will hang
			 * indefinitely blocking on the input stream with no data to read.
			 */
			if (stayOpen && line.equals("{ready}"))
				break;
		}

		// Print out how long the call to external ExifToolNew3 process took.
		log("\tFinished reading ExifToolNew3 response in %d ms.",
				(System.currentTimeMillis() - exifToolCallElapsedTime));

		/*
		 * If we are not using a persistent ExifToolNew3 process, then after running the command above, the process
		 * exited in which case we need to clean our streams up since it no longer exists. If we were using a persistent
		 * ExifToolNew3 process, leave the streams open for future calls.
		 */
		if (!stayOpen)
			streams.close();

		if (DEBUG)
			log("\tImage Meta Processed in %d ms [queried found %d values]",
					(System.currentTimeMillis() - startTime), resultMap.size());

		return resultMap;
	}

	public void setImageMeta(File image, Map<Tag, String> tags) throws IllegalArgumentException, SecurityException,
			IOException {
		setImageMeta(image, Format.NUMERIC, tags);
	}

	public void setImageMeta(File image, Format format, Map<Tag, String> tags) throws IllegalArgumentException,
			SecurityException, IOException {
		if (image == null)
			throw new IllegalArgumentException("image cannot be null and must be a valid stream of image data.");
		if (format == null)
			throw new IllegalArgumentException("format cannot be null");
		if (tags == null || tags.size() == 0)
			throw new IllegalArgumentException(
					"tags cannot be null and must contain 1 or more Tag to query the image for.");
		if (!image.canWrite())
			throw new SecurityException(
					"Unable to read the given image ["
							+ image.getAbsolutePath()
							+ "], ensure that the image exists at the given path and that the executing Java process has permissions to read it.");

		long startTime = System.currentTimeMillis();

		if (DEBUG)
			log("Writing %d tags to image: %s", tags.size(), image.getAbsolutePath());

		long exifToolCallElapsedTime = 0;

		/*
		 * Using ExifToolNew3 in daemon mode (-stay_open True) executes different code paths below. So establish the
		 * flag for this once and it is reused a multitude of times later in this method to figure out where to branch
		 * to.
		 */
		boolean stayOpen = featureSet.contains(Feature.STAY_OPEN);

		// Clear process args
		args.clear();

		if (stayOpen) {
			log("\tUsing ExifToolNew3 in daemon mode (-stay_open True)...");

			// Always reset the cleanup task.
			resetCleanupTask();

			/*
			 * If this is our first time calling getImageMeta with a stayOpen connection, set up the persistent process
			 * and run it so it is ready to receive commands from us.
			 */
			if (streams == null) {
				log("\tStarting daemon ExifToolNew3 process and creating read/write streams (this only happens once)...");

				args.add(EXIF_TOOL_PATH);
				args.add("-stay_open");
				args.add("True");
				args.add("-@");
				args.add("-");

				// Begin the persistent ExifToolNew3 process.
				streams = startExifToolProcess(args);
			}

			log("\tStreaming arguments to ExifToolNew3 process...");

			if (format == Format.NUMERIC)
				streams.writer.write("-n\n"); // numeric output

			streams.writer.write("-S\n"); // compact output

			for (Entry<Tag, String> entry : tags.entrySet()) {
				streams.writer.write('-');
				streams.writer.write(entry.getKey().getKey());
				streams.writer.write("='");
				streams.writer.write(entry.getValue());
				streams.writer.write("'\n");
			}

			streams.writer.write(image.getAbsolutePath());
			streams.writer.write("\n");

			log("\tExecuting ExifToolNew3...");

			// Begin tracking the duration ExifToolNew3 takes to respond.
			exifToolCallElapsedTime = System.currentTimeMillis();

			// Run ExifToolNew3 on our file with all the given arguments.
			streams.writer.write("-execute\n");
			streams.writer.flush();
		} else {
			log("\tUsing ExifToolNew3 in non-daemon mode (-stay_open False)...");

			/*
			 * Since we are not using a stayOpen process, we need to setup the execution arguments completely each time.
			 */
			args.add(EXIF_TOOL_PATH);

			if (format == Format.NUMERIC)
				args.add("-n"); // numeric output

			args.add("-S"); // compact output

			for (Entry<Tag, String> entry : tags.entrySet())
				args.add("-" + entry.getKey().getKey() + "='" + entry.getValue() + "'");

			args.add(image.getAbsolutePath());

			// Run the ExifToolNew3 with our args.
			streams = startExifToolProcess(args);

			// Begin tracking the duration ExifToolNew3 takes to respond.
			exifToolCallElapsedTime = System.currentTimeMillis();
		}

		log("\tReading response back from ExifToolNew3...");

		String line = null;

		while ((line = streams.reader.readLine()) != null) {
			/*
			 * When using a persistent ExifToolNew3 process, it terminates its output to us with a "{ready}" clause on a
			 * new line, we need to look for it and break from this loop when we see it otherwise this process will hang
			 * indefinitely blocking on the input stream with no data to read.
			 */
			if (stayOpen && line.equals("{ready}"))
				break;
		}

		// Print out how long the call to external ExifToolNew3 process took.
		log("\tFinished reading ExifToolNew3 response in %d ms.",
				(System.currentTimeMillis() - exifToolCallElapsedTime));

		/*
		 * If we are not using a persistent ExifToolNew3 process, then after running the command above, the process
		 * exited in which case we need to clean our streams up since it no longer exists. If we were using a persistent
		 * ExifToolNew3 process, leave the streams open for future calls.
		 */
		if (!stayOpen)
			streams.close();

		if (DEBUG)
			log("\tImage Meta Processed in %d ms [write %d tags]", (System.currentTimeMillis() - startTime),
					tags.size());
	}

	/**
	 * Helper method used to make canceling the current task and scheduling a new one easier.
	 * <p/>
	 * It is annoying that we cannot just reset the timer on the task, but that isn't the way the java.util.Timer class
	 * was designed unfortunately.
	 */
	void resetCleanupTask() {
		// no-op if the timer was never created.
		if (cleanupTimer == null)
			return;

		log("\tResetting cleanup task...");

		// Cancel the current cleanup task if necessary.
		if (currentCleanupTask != null)
			currentCleanupTask.cancel();

		// Schedule a new cleanup task.
		cleanupTimer.schedule((currentCleanupTask = new CleanupTimerTask(this)), PROCESS_CLEANUP_DELAY,
				PROCESS_CLEANUP_DELAY);

		log("\t\tSuccessful");
	}

	@Override
	public void startup() {
		resetCleanupTask();
	}

	@Override
	public void shutdown() {
		shutdownCleanupTask();
	}

	@Override
	public boolean isStayOpen() {
		return featureSet.contains(Feature.STAY_OPEN);
	}

	@Override
	public <T> void addImageMetadata(File image, Map<T, Object> values) throws IOException {
		setImageMeta(image, (Map) values);
	}

	@Override
	@Deprecated
	public <T> void writeMetadata(WriteOptions options, File image, Map<T, Object> values) throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	protected void finalize() throws Throwable {
		ExifToolNew3.log.info("ExifToolNew3 not used anymore shutdown the exiftool process...");
		shutdown();
		super.finalize();
	}

	@Override
	@Deprecated
	public void rebuildMetadata(File file) throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	@Deprecated
	public void rebuildMetadata(WriteOptions options, File file) throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public List<String> execute(List<String> args) {
		//return startExifToolProcess(args);
		throw new RuntimeException("Not implemented yet!");
	}
}
