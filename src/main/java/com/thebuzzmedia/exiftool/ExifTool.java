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
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class used to provide a Java-like interface to Phil Harvey's excellent,
 * Perl-based <a
 * href="http://www.sno.phy.queensu.ca/~phil/exiftool">ExifTool</a>.
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
 * system path or pointed to by {@link #EXIF_TOOL_PATH}, using this class to
 * communicate with ExifTool is as simple as creating an instance (
 * <code>ExifTool tool = new ExifTool()</code>) and then making calls to
 * {@link #getImageMeta(File, Tag...)} or
 * {@link #getImageMeta(File, Format, Tag...)} with a list of {@link Tag}s you
 * want to pull values for from the given image.
 * <p/>
 * In this default mode, calls to <code>getImageMeta</code> will automatically
 * start an external ExifTool process to handle the request. After ExifTool has
 * parsed the tag values from the file, the external process exits and this
 * class parses the result before returning it to the caller.
 * <p/>
 * Results from calls to <code>getImageMeta</code> are returned in a {@link Map}
 * with the {@link Tag} values as the keys and {@link String} values for every
 * tag that had a value in the image file as the values. {@link Tag}s with no
 * value found in the image are omitted from the result map.
 * <p/>
 * While each {@link Tag} provides a hint at which format the resulting value
 * for that tag is returned as from ExifTool (see {@link Tag#getType()}), that
 * only applies to values returned with an output format of
 * {@link Format#NUMERIC} and it is ultimately up to the caller to decide how
 * best to parse or convert the returned values.
 * <p/>
 * The {@link Tag} Enum provides the {@link Tag#parseValue(String)}
 * convenience method for parsing given <code>String</code> values according to
 * the Tag hint automatically for you if that is what you plan on doing,
 * otherwise feel free to handle the return values anyway you want.
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
 * by {@link #EXIF_TOOL_PATH} before successfully instantiating the class and
 * will notify you via an {@link UnsupportedFeatureException} if the native
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
 * {@link #PROCESS_CLEANUP_DELAY} system variable. A value of <code>0</code> or
 * less disabled the automatic cleanup process and requires you to cleanup
 * ExifTool instances on your own by calling {@link #close()} manually.
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
public class ExifTool {

  public static final String ENV_EXIF_TOOL_PATH = "exiftool.path";
  public static final String ENV_EXIF_TOOL_PROCESSCLEANUPDELAY = "exiftool.processCleanupDelay";
  public static final long DEFAULT_PROCESS_CLEANUP_DELAY = 0;

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

  /**
   * Compiled {@link Pattern} of ": " used to split compact output from
   * ExifTool evenly into name/value pairs.
   */
  private static final Pattern TAG_VALUE_PATTERN = Pattern.compile("\\s*:\\s*");

  private static Logger log = LoggerFactory.getLogger(ExifTool.class);

  /**
   * The absolute path to the ExifTool executable on the host system running
   * this class as defined by the "<code>exiftool.path</code>" system
   * property.
   * <p/>
   * If ExifTool is on your system path and running the command "exiftool"
   * successfully executes it, leaving this value unchanged will work fine on
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
  private final String EXIF_TOOL_PATH;

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
   * Default value is <code>600,000</code> (10 minutes).
   */
  private final long PROCESS_CLEANUP_DELAY;

	/**
	 * Map shared across all instances of this class that maintains the state of
	 * {@link Feature}s and if they are supported or not (supported=true,
	 * unsupported=false) by the underlying native ExifTool process being used
	 * in conjunction with this class.
	 * <p/>
	 * If a {@link Feature} is missing from the map (has no <code>true</code> or
	 * <code>false</code> flag associated with it, but <code>null</code>
	 * instead) then that means that feature has not been checked for support
	 * yet and this class will know to call
	 * {@link #checkFeatureSupport(Feature...)} on it to determine its supported
	 * state.
	 * <p/>
	 * For efficiency reasons, individual {@link Feature}s are checked for
	 * support one time during each run of the VM and never again during the
	 * session of that running VM.
	 */
  private final Map<Feature, Boolean> FEATURE_SUPPORT_MAP = new HashMap<ExifTool.Feature, Boolean>();

	/**
	 * List of args used to execute ExifTool using the '-ver' flag in
	 * order to get it to print out its version number. Used by the
	 * {@link #checkFeatureSupport(Feature...)} method to check all the required
	 * feature versions.
	 */
  private final List<String> ARGS_VERIFY_FEATURE;
  private final List<String> ARGS_STAY_OPEN;
  private final Set<Feature> featureSet;

  private final Timer cleanupTimer;
  private TimerTask currentCleanupTask = null;
  private IOStream stream;

  public ExifTool(){
    this((Feature[]) null);
  }

  /**
   * In this constructor, exifToolPath and processCleanupDelay are gotten from system properties
   * exiftool.path and exiftool.processCleanupDelay. processCleanupDelay is optional. If not found,
   * the default is used.
   * @param features
   */
  public ExifTool (Feature ... features){
    this(
      System.getProperty(ENV_EXIF_TOOL_PATH, "exiftool"),
      Long.getLong(ENV_EXIF_TOOL_PROCESSCLEANUPDELAY, DEFAULT_PROCESS_CLEANUP_DELAY),
      features
    );
  }

  public ExifTool(String exifToolPath) {
    this(exifToolPath, DEFAULT_PROCESS_CLEANUP_DELAY, (Feature[]) null);
  }

  public ExifTool(String exifToolPath, Feature ... features) {
    this(exifToolPath, DEFAULT_PROCESS_CLEANUP_DELAY, features);
  }

  public ExifTool(String exifToolPath, long processCleanupDelay, Feature ... features) {
    this.EXIF_TOOL_PATH = exifToolPath;
    this.PROCESS_CLEANUP_DELAY = processCleanupDelay;

    ARGS_VERIFY_FEATURE = Arrays.asList(EXIF_TOOL_PATH, "-ver");
    ARGS_STAY_OPEN = Arrays.asList(EXIF_TOOL_PATH, "-stay_open", "True", "-@", "-");

    featureSet = new HashSet<ExifTool.Feature>();

    if (features != null && features.length > 0) {
     /*
      * Process all features to ensure we checked them for support in the
      * installed version of ExifTool. If the feature has already been
      * checked before, this method will return immediately.
      */
      checkFeatureSupport(features);

     /*
      * Now we need to verify that all the features requested for this
      * instance of ExifTool to use WERE supported after all.
      */
      for (Feature feature : features) {

       /*
        * If the Feature was supported, record it in the local
      	* featureSet so this instance knows what features are being
      	* turned on by the caller.
      	*
      	* If the Feature was not supported, throw an exception
      	* reporting it to the caller so they know it cannot be used.
      	*/
        if (FEATURE_SUPPORT_MAP.get(feature)){
          featureSet.add(feature);
        } else {
          throw new UnsupportedFeatureException(feature);
        }
      }
    }


   /*
    * Now that initialization is done, init the cleanup timer if we are
    * using STAY_OPEN and the delay time set is non-zero.
    */
    if (isFeatureEnabled(Feature.STAY_OPEN) && PROCESS_CLEANUP_DELAY > 0) {
      this.cleanupTimer = new Timer(CLEANUP_THREAD_NAME, true);

      // Start the first cleanup task counting down.
      resetCleanupTask();
    } else {
      cleanupTimer = null;
    }
  }

  /**
	 * Used to determine if the given {@link Feature} is supported by the
	 * underlying native install of ExifTool pointed at by
	 * {@link #EXIF_TOOL_PATH}.
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
		if (feature == null){
			throw new IllegalArgumentException("feature cannot be null");
    }

		Boolean supported = FEATURE_SUPPORT_MAP.get(feature);

		/*
		 * If there is no Boolean flag for the feature, support for it hasn't
		 * been checked yet with the native ExifTool install, so we need to do
		 * that.
		 */
		if (supported == null) {
			log.debug("Support for feature %s has not been checked yet, checking...");
			checkFeatureSupport(feature);

			// Re-query for the supported state
			supported = FEATURE_SUPPORT_MAP.get(feature);
		}

		return supported;
	}

	/**
	 * Used to verify the version of ExifTool installed is a high enough version
	 * to support the given features.
	 * <p/>
	 * This method runs the command "<code>exiftool -ver</code>" to get the
	 * version of the installed ExifTool and then compares that version to the
	 * least required version specified by the given features (see
	 * {@link Feature#getVersion()}).
	 * 
	 * @param features
	 *            The features whose required versions will be checked against
	 *            the installed ExifTool for support.
	 * 
	 * @throws RuntimeException
	 *             if any exception occurs communicating with the external
	 *             ExifTool process spun up in order to check its version.
	 */
  protected void checkFeatureSupport(Feature... features) throws RuntimeException {
    // Ensure there is work to do.
    if (features == null || features.length == 0)
      return;

    log.debug(String.format("Checking %d feature(s) for support in the external ExifTool install...",features.length));

    for (Feature feature : features) {
      String ver = null;
      Boolean supported;

      log.debug(String.format("\tChecking feature %s for support, requires ExifTool version %s or higher...", feature, feature.version));

      // Execute 'exiftool -ver'
      IOStream streams = startExifToolProcess(ARGS_VERIFY_FEATURE);

      try {
        // Read the single-line reply (version number)
        ver = streams.reader.readLine();
      } catch (Exception e) {
        /*
				 * no-op, while it is important to know that we COULD launch the
				 * ExifTool process (i.e. startExifToolProcess call worked) but
				 * couldn't communicate with it, the context with which this
				 * method is called is from the constructor of this class which
				 * would just wrap this exception and discard it anyway if it
				 * failed.
				 * 
				 * the caller will realize there is something wrong with the
				 * ExifTool process communication as soon as they make their
				 * first call to getImageMeta in which case whatever was causing
				 * the exception here will popup there and then need to be
				 * corrected.
				 * 
				 * This is an edge case that should only happen in really rare
				 * scenarios, so making this method easier to use is more
				 * important that robust IOException handling right here.
				 */

      } finally {
        // Close r/w streams to exited process.
        streams.close();
      }
      // Ensure the version found is >= the required version.
      if (ver != null && ver.compareTo(feature.version) >= 0) {
        supported = Boolean.TRUE;
        log.debug(String.format("\tFound ExifTool version %s, feature %s is SUPPORTED.",
                ver, feature));
      } else {
        supported = Boolean.FALSE;
        log.debug(String.format("\tFound ExifTool version %s, feature %s is NOT SUPPORTED.",
                ver, feature));
      }

      // Update feature support map
      FEATURE_SUPPORT_MAP.put(feature, supported);
    }
  }

	protected IOStream startExifToolProcess(List<String> args) throws RuntimeException {
		Process proc;
		IOStream stream;

		log.debug(String.format("Attempting to start external ExifTool process using args: %s", args));

		try {
			proc = new ProcessBuilder(args).start();
			log.debug("\tSuccessful");
		} catch (Exception e) {
			String message = "Unable to start external ExifTool process using the execution arguments: "
					+ args
					+ ". Ensure ExifTool is installed correctly and runs using the command path '"
					+ EXIF_TOOL_PATH
					+ "' as specified by the 'exiftool.path' system property.";

      log.debug(message);
			throw new RuntimeException(message, e);
		}

		log.debug("Setting up Read/Write streams to the external ExifTool process...");

		// Setup read/write streams to the new process.
		stream = new IOStream(new BufferedReader(new InputStreamReader(
				proc.getInputStream())), new OutputStreamWriter(
				proc.getOutputStream()));

		log.debug("\tSuccessful, returning stream to caller.");
		return stream;
	}

	/**
	 * Simple class used to house the read/write streams used to communicate
	 * with an external ExifTool process as well as the logic used to safely
	 * close the streams when no longer needed.
	 * <p/>
	 * This class is just a convenient way to group and manage the read/write
	 * streams as opposed to making them dangling member variables off of
	 * ExifTool directly.
	 * 
	 * @author Riyad Kalla (software@thebuzzmedia.com)
	 * @since 1.1
	 */
	private static class IOStream {
		BufferedReader reader;
		OutputStreamWriter writer;

		public IOStream(BufferedReader reader, OutputStreamWriter writer) {
			this.reader = reader;
			this.writer = writer;
		}

		public void close() {
			try {
				log.debug("Closing Read stream...");
				reader.close();
				log.debug("\tSuccessful");
			} catch (Exception e) {
				// no-op, just try to close it.
			}

			try {
				log.debug("Closing Write stream...");
				writer.close();
				log.debug("\tSuccessful");
			} catch (Exception e) {
				// no-op, just try to close it.
			}

			// Null the stream references.
			reader = null;
			writer = null;

			log.debug("Read/Write streams successfully closed.");
		}
	}

	/**
	 * Enum used to define the different kinds of features in the native
	 * ExifTool executable that this class can help you take advantage of.
	 * <p/>
	 * These flags are different from {@link Tag}s in that a "feature" is
	 * determined to be a special functionality of the underlying ExifTool
	 * executable that requires a different code-path in this class to take
	 * advantage of; for example, <code>-stay_open True</code> support.
	 * 
	 * @author Riyad Kalla (software@thebuzzmedia.com)
	 * @since 1.1
	 */
	public enum Feature {
		/**
		 * Enum used to specify that you wish to launch the underlying ExifTool
		 * process with <code>-stay_open True</code> support turned on that this
		 * class can then take advantage of.
		 * <p/>
		 * Required ExifTool version is <code>8.36</code> or higher.
		 */
		STAY_OPEN("8.36");

		/**
		 * Used to get the version of ExifTool required by this feature in order
		 * to work.
		 * 
		 * @return the version of ExifTool required by this feature in order to
		 *         work.
		 */
		public String getVersion() {
			return version;
		}

		private String version;

		private Feature(String version) {
			this.version = version;
		}
	}

	/**
	 * Enum used to define the 2 different output formats that {@link Tag}
	 * values can be returned in: numeric or human-readable text.
	 * <p/>
	 * ExifTool, via the <code>-n</code> command line arg, is capable of
	 * returning most values in their raw numeric form (e.g.
	 * Aperture="2.8010323841") as well as a more human-readable/friendly format
	 * (e.g. Aperture="2.8").
	 * <p/>
	 * While the {@link Tag}s defined on this class do provide a hint at the
	 * type of the result (see {@link Tag#getType()}), that hint only applies
	 * when the {@link Format#NUMERIC} form of the value is returned.
	 * <p/>
	 * If the caller finds the human-readable format easier to process,
	 * {@link Format#HUMAN_READABLE} can be specified when calling
	 * {@link ExifTool#getImageMeta(File, Format, Tag...)} and the returned
	 * {@link String} values processed manually by the caller.
	 * <p/>
	 * In order to see the types of values that are returned when
	 * {@link Format#HUMAN_READABLE} is used, you can check the comprehensive <a
	 * href="http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html">
	 * ExifTool Tag Guide</a>.
	 * <p/>
	 * This makes sense with some values like Aperture that in
	 * {@link Format#NUMERIC} format end up returning as 14-decimal-place, high
	 * precision values that are near the intended value (e.g.
	 * "2.79999992203711" instead of just returning "2.8"). On the other hand,
	 * other values (like Orientation) are easier to parse when their numeric
	 * value (1-8) is returned instead of a much longer friendly name (e.g.
	 * "Mirror horizontal and rotate 270 CW").
	 * 
	 * @author Riyad Kalla (software@thebuzzmedia.com)
	 * @since 1.1
	 */
	public enum Format {
		NUMERIC, HUMAN_READABLE
	}

	/**
	 * Enum used to pre-define a convenient list of tags that can be easily
	 * extracted from images using this class with an external install of
	 * ExifTool.
	 * <p/>
	 * Each tag defined also includes a type hint for the parsed value
	 * associated with it when the default {@link Format#NUMERIC} value format
	 * is used.
	 * <p/>
	 * All replies from ExifTool are parsed as {@link String}s and using the
	 * type hint from each {@link Tag} can easily be converted to the correct
	 * data format by using the provided {@link Tag#parseValue(String)}
	 * method.
	 * <p/>
	 * This class does not make an attempt at converting the value automatically
	 * in case the caller decides they would prefer tag values returned in
	 * {@link Format#HUMAN_READABLE} format and to avoid any compatibility
	 * issues with future versions of ExifTool if a tag's return value is
	 * changed. This approach to leaving returned tag values as strings until
	 * the caller decides they want to parse them is a safer and more robust
	 * approach.
	 * <p/>
	 * The types provided by each tag are merely a hint based on the <a
	 * href="http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html"
	 * >ExifTool Tag Guide</a> by Phil Harvey; the caller is free to parse or
	 * process the returned {@link String} values any way they wish.
	 * <h3>Tag Support</h3>
	 * ExifTool is capable of parsing almost every tag known to man (1000+), but
	 * this class makes an attempt at pre-defining a convenient list of the most
	 * common tags for use.
	 * <p/>
	 * This list was determined by looking at the common metadata tag values
	 * written to images by popular mobile devices (iPhone, Android) as well as
	 * cameras like simple point and shoots as well as DSLRs. As an additional
	 * source of input the list of supported/common EXIF formats that Flickr
	 * supports was also reviewed to ensure the most common/useful tags were
	 * being covered here.
	 * <p/>
	 * Please email me or <a
	 * href="https://github.com/thebuzzmedia/imgscalr/issues">file an issue</a>
	 * if you think this list is missing a commonly used tag that should be
	 * added to it.
	 * 
	 * @author Riyad Kalla (software@thebuzzmedia.com)
	 * @since 1.1
	 */
  public enum Tag {
    //single entry tags
    APERTURE("ApertureValue", Double.class),
    AUTHOR("XPAuthor", String.class),
    COLOR_SPACE("ColorSpace", Integer.class),
    COMMENT("XPComment", String.class),
    CONTRAST("Contrast", Integer.class),
    CREATE_DATE("CreateDate", String.class),
    CREATION_DATE("CreationDate", String.class),
    DATE_CREATED("DateCreated", String.class),
    DATE_TIME_ORIGINAL("DateTimeOriginal", String.class),
    DIGITAL_ZOOM_RATIO("DigitalZoomRatio", Double.class),
    EXIF_VERSION("ExifVersion", String.class),
    EXPOSURE_COMPENSATION("ExposureCompensation", Double.class),
    EXPOSURE_PROGRAM("ExposureProgram", Integer.class),
    EXPOSURE_TIME("ExposureTime", Double.class),
    FLASH("Flash", Integer.class),
    FOCAL_LENGTH("FocalLength", Double.class),
    FOCAL_LENGTH_35MM("FocalLengthIn35mmFormat", Integer.class),
    FNUMBER("FNumber", String.class),
    GPS_ALTITUDE("GPSAltitude", Double.class),
    GPS_ALTITUDE_REF("GPSAltitudeRef", Integer.class),
    GPS_BEARING("GPSDestBearing", Double.class),
    GPS_BEARING_REF("GPSDestBearingRef", String.class),
    GPS_DATESTAMP("GPSDateStamp", String.class),
    GPS_LATITUDE("GPSLatitude", Double.class),
    GPS_LATITUDE_REF("GPSLatitudeRef", String.class),
    GPS_LONGITUDE("GPSLongitude", Double.class),
    GPS_LONGITUDE_REF("GPSLongitudeRef", String.class),
    GPS_PROCESS_METHOD("GPSProcessingMethod", String.class),
    GPS_SPEED("GPSSpeed", Double.class),
    GPS_SPEED_REF("GPSSpeedRef", String.class),
    GPS_TIMESTAMP("GPSTimeStamp", String.class),
    IMAGE_HEIGHT("ImageHeight", Integer.class),
    IMAGE_WIDTH("ImageWidth", Integer.class),
    ISO("ISO", Integer.class),
    KEYWORDS("XPKeywords", String.class),
    LENS_MAKE("LensMake", String.class),
    LENS_MODEL("LensModel", String.class),
    MAKE("Make", String.class),
    METERING_MODE("MeteringMode", Integer.class),
    MODEL("Model", String.class),
    ORIENTATION("Orientation", Integer.class),
    OWNER_NAME("OwnerName", String.class),
    RATING("Rating", Integer.class),
    RATING_PERCENT("RatingPercent", Integer.class),
    ROTATION("Rotation", Integer.class),
    SATURATION("Saturation", Integer.class),
    SENSING_METHOD("SensingMethod", Integer.class),
    SHARPNESS("Sharpness", Integer.class),
    SHUTTER_SPEED("ShutterSpeedValue", Double.class),
    SOFTWARE("Software", String.class),
    SUBJECT("XPSubject", String.class),
    TITLE("XPTitle", String.class),
    WHITE_BALANCE("WhiteBalance", Integer.class),
    X_RESOLUTION("XResolution", Double.class),
    Y_RESOLUTION("YResolution", Double.class),
  ;

    /**
     * Used to get the {@link Tag} identified by the given, case-sensitive,
     * tag name.
     *
     * @param name
     *            The case-sensitive name of the tag that will be searched
     *            for.
     *
     * @return the {@link Tag} identified by the given, case-sensitive, tag
     *         name or <code>null</code> if one couldn't be found.
     */
    public static Tag forName(String name) {
      for (Tag tag : Tag.values()){
        if (tag.getName().equals(name)){
          return tag;
        }
      }
      return null;
    }

		/**
		 * Convenience method used to convert the given string Tag value
		 * (returned from the external ExifTool process) into the type described
		 * by the associated {@link Tag}.
		 * 
		 * @param <T>
		 *            The type of the returned value.
		 * @param value
		 *            The {@link String} representation of the tag's value as
		 *            parsed from the image.
		 * 
		 * @return the given string value converted to a native Java type (e.g.
		 *         Integer, Double, etc.).
		 * 
		 * @throws IllegalArgumentException
		 *             if <code>tag</code> is <code>null</code>.
		 * @throws NumberFormatException
		 *             if any exception occurs while trying to parse the given
		 *             <code>value</code> to any of the supported numeric types
		 *             in Java via calls to the respective <code>parseXXX</code>
		 *             methods defined on all the numeric wrapper classes (e.g.
		 *             {@link Integer#parseInt(String)} ,
		 *             {@link Double#parseDouble(String)} and so on).
		 * @throws ClassCastException
		 *             if the type defined by <code>T</code> is incompatible
		 *             with the type defined by {@link Tag#getType()} returned
		 *             by the <code>tag</code> argument passed in. This class
		 *             performs an implicit/unchecked cast to the type
		 *             <code>T</code> before returning the parsed result of the
		 *             type indicated by {@link Tag#getType()}. If the types do
		 *             not match, a <code>ClassCastException</code> will be
		 *             generated by the VM.
		 */
		@SuppressWarnings("unchecked")
		public <T> T parseValue(String value) throws IllegalArgumentException {

			if (value != null) {
        if (Boolean.class.isAssignableFrom(this.getType())){
          return (T) Boolean.valueOf(value);
        } else if (Byte.class.isAssignableFrom(this.getType())){
          return (T) Byte.valueOf(Byte.parseByte(value));
        } else if (Integer.class.isAssignableFrom(this.getType())){
          return  (T) Integer.valueOf(Integer.parseInt(value));
        } else if (Short.class.isAssignableFrom(this.getType())){
          return  (T) Short.valueOf(Short.parseShort(value));
        } else if (Long.class.isAssignableFrom(this.getType())){
          return  (T) Long.valueOf(Long.parseLong(value));
        } else if (Float.class.isAssignableFrom(this.getType())){
          return (T) Float.valueOf(Float.parseFloat(value));
        } else if (Double.class.isAssignableFrom(this.getType())){
          return  (T) parseDouble(value);
        } else if (Character.class.isAssignableFrom(this.getType())){
          return  (T) Character.valueOf(value.charAt(0));
        } else if (String.class.isAssignableFrom(this.getType())){
          return  (T) value;
        }
      }
			return null;
		}

    private Double parseDouble(String in) {
      if (in.contains("/")) {
        String[] enumeratorAndDivisor = in.split("/");
        return Double.parseDouble(enumeratorAndDivisor[0]) / Double.parseDouble(enumeratorAndDivisor[1]);
      } else {
        return Double.parseDouble(in);
      }
    }

		/**
		 * Used to get the name of the tag (e.g. "Orientation", "ISO", etc.).
		 * 
		 * @return the name of the tag (e.g. "Orientation", "ISO", etc.).
		 */
		public String getName() {
			return name;
		}

		/**
		 * Used to get a hint for the native type of this tag's value as
		 * specified by Phil Harvey's <a href=
		 * "http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html"
		 * >ExifTool Tag Guide</a>.
		 * 
		 * @return a hint for the native type of this tag's value.
		 */
		public Class<?> getType() {
			return type;
		}

		private String name;
		private Class<?> type;

		private Tag(String name, Class<?> type) {
			this.name = name;
			this.type = type;
		}
	}

  public enum TagGroup {
    EXIF("EXIF","exif:all"),
    IPTC("IPTC", "iptc:all"),
    XMP("XMP", "xmp:all"),
    ALL("ALL", "all");

    private final String name;
    private final String value;

    private TagGroup(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public String getValue() {
      return value;
    }
  }

  /**
   * Used to startup the external ExifTool process and open the read/write
   * streams used to communicate with it when {@link Feature#STAY_OPEN} is
   * enabled. This method has no effect if the stay open feature is not enabled.
   */
  public void startup(){
    if (featureSet.contains(Feature.STAY_OPEN)){
      if (!isRunning()) {
        synchronized (this){
          if (!isRunning()){
            log.debug("Starting daemon ExifTool process and creating read/write streams (this only happens once)...");
            stream = startExifToolProcess(ARGS_STAY_OPEN);
          }
        }
      }
    }
  }

  /**
   * This is same as {@link #close()}, added for consistency with {@link #startup()}
   */
  public void shutdown(){
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
	 * The cleanup thread will automatically call this after an interval of
	 * inactivity defined by {@link #PROCESS_CLEANUP_DELAY}.
	 * <p/>
	 * Calling this method on an instance of this class without
	 * {@link Feature#STAY_OPEN} support enabled has no effect.
	 */
	public void close() {
		/*
		 * no-op if the underlying process and streams have already been closed
		 * OR if stayOpen was never used in the first place in which case
		 * nothing is open right now anyway.
		 */
		if (stream == null){
      log.debug("This ExifTool instance was never used so no external process or streams were ever created (nothing to clean up, we will just exit).");
			return;
    }

		/*
		 * If ExifTool was used in stayOpen mode but getImageMeta was never
		 * called then the streams were never initialized and there is nothing
		 * to shut down or destroy, otherwise we need to close down all the
		 * resources in use.
		 */
    try {
      log.debug("Attempting to close ExifTool daemon process, issuing '-stay_open\\nFalse\\n' command...");

      // Tell the ExifTool process to exit.
      stream.writer.write("-stay_open\nFalse\n");
      stream.writer.flush();

      log.debug("\tSuccessful");
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    } finally {
      stream.close();
    }

    stream = null;
		log.debug("ExifTool daemon process successfully terminated.");

    if(currentCleanupTask != null) {
      currentCleanupTask.cancel();
    }
    currentCleanupTask = null;
    if(cleanupTimer != null) {
      cleanupTimer.cancel();
    }
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
		return (stream != null);
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
	public boolean isFeatureEnabled(Feature feature) throws IllegalArgumentException {
		if (feature == null){
			throw new IllegalArgumentException("feature cannot be null");
    }
		return featureSet.contains(feature);
	}

    public void addImageMetadata(File image, Map<Tag, Object> values) throws IOException {

        if (image == null){
            throw new IllegalArgumentException("image cannot be null and must be a valid stream of image data.");
        }
        if (values == null || values.isEmpty()){
            throw new IllegalArgumentException("values cannot be null and must contain 1 or more tag to value mappings");
        }

        if (!image.canWrite()){
            throw new SecurityException("Unable to write the given image [" + image.getAbsolutePath()
                    + "], ensure that the image exists at the given path and that the executing Java process has permissions to write to it.");
        }

        log.info("Adding Tags {} to {}", values, image.getAbsolutePath());

        if(featureSet.contains(Feature.STAY_OPEN)) {
            execute(createCommand(image.getAbsolutePath(), values));
        } else {
            execute(createCommandList(image.getAbsolutePath(), values));
        }
    }

    private String createCommand(String filename, Map<Tag, Object> values) {
        StringBuilder commandBuf = new StringBuilder();

        for(Map.Entry<Tag, Object> entry : values.entrySet()) {
            Tag tag = entry.getKey();
            Object value = entry.getValue();

            if(value instanceof Number) {
                commandBuf.append("-n\n");
            }
            commandBuf.append("-");
            commandBuf.append(tag.getName());

            if(value instanceof Number) {
                commandBuf.append("#");
            }
            commandBuf.append("=");
            if(value != null) {
                if(value instanceof String) {
                    commandBuf.append(value.toString());
                } else {
                    commandBuf.append(value.toString());
                }
            }
            commandBuf.append("\n");

        }
        commandBuf.append(filename);
        commandBuf.append("\n");

        return commandBuf.toString();
    }

    private List<String> createCommandList(String filename, Map<Tag, Object> values) {

        List<String> args = new ArrayList<String>(64);

        for(Map.Entry<Tag, Object> entry : values.entrySet()) {
            Tag tag = entry.getKey();
            Object value = entry.getValue();

            StringBuilder arg = new StringBuilder();
            arg.append("-").append(tag.getName());
            if (value instanceof Number){
                arg.append("#");
            }
            arg.append("=");
            if(value != null) {
                if(value instanceof String) {
                    arg.append("\"").append(value.toString()).append("\"");
                } else {
                    arg.append(value.toString());
                }
            }
            args.add(arg.toString());

        }

        args.add(filename);
        return args;

    }

    /**
     * Internal method to execute a command in standalone mode
     */
    private void execute(final List<String> args) throws IOException {
        long startTime = System.currentTimeMillis();
        long exifToolCallStartTime;

        log.debug("Executing $ exiftool {}", args);
        log.debug("Using ExifTool in non-daemon mode (-stay_open False)...");

        Map<String, String> resultMap;

        /*
         * Since we are not using a stayOpen process, we need to setup the
         * execution arguments completely each time.
         */
        List<String> commandArg = new ArrayList<String>(64);
        commandArg.add(EXIF_TOOL_PATH);
        commandArg.addAll(args);

        // Run the ExifTool with our args.
        try {
            stream = startExifToolProcess(commandArg);

            // Begin tracking the duration ExifTool takes to respond.
            exifToolCallStartTime = System.currentTimeMillis();
            resultMap = readResponse(stream, false);
            log.debug("results = {} ", resultMap);

            long exifToolElapsedTime = System.currentTimeMillis()-exifToolCallStartTime;

            // Print out how long the call to external ExifTool process took.
            if (log.isDebugEnabled()){
                log.debug(String.format("Finished reading ExifTool response in %d ms.", exifToolElapsedTime));
            }
        } finally {
            /*
             * If we are not using a persistent ExifTool process, then after running
             * the command above, the process exited in which case we need to clean
             * our streams up since it no longer exists.
             */
            stream.close();
        }

        log.debug("Image Meta Processed in {} ms",(System.currentTimeMillis() - startTime));
    }

    private void execute(final String command) throws IOException {
        long exifToolCallStartTime;
        Map<String, String> resultMap;

        log.debug("Executing $ exiftool {}", command);
        log.debug("Using ExifTool in daemon mode (-stay_open True)...");

        // Always reset the cleanup task.
        resetCleanupTask();
        if (!isRunning()){
            startup();
        }

        log.debug("Streaming arguments to ExifTool process...");

        synchronized (this){
            boolean success = false;
            int attempts = 0;
            exifToolCallStartTime = System.currentTimeMillis();
            while (!success && attempts <= 2){
                attempts++;

                stream.writer.write(command);

                log.debug("Executing ExifTool...");

                // Begin tracking the duration ExifTool takes to respond.
                exifToolCallStartTime = System.currentTimeMillis();

                // Run ExifTool on our file with all the given arguments.
                stream.writer.write("-execute\n");
                try {
                    stream.writer.flush();
                    success = true;
                } catch (IOException e){
                    success = false;
                    //only catch "Stream Closed" error (happens when process has died
                    if (!e.getMessage().equals("Stream closed")){
                        throw e; //
                    }
                    log.warn("Caught IOException / Stream closed, trying to restart daemon");
                    stream = null;
                    startup();
                }
            }
            resultMap = readResponse(stream, true);
            log.debug("results = {} ", resultMap);
        }

        long exifToolElapsedTime = System.currentTimeMillis()-exifToolCallStartTime;
        // Print out how long the call to external ExifTool process took.

        log.debug("Finished reading ExifTool response in {} ms.", exifToolElapsedTime);
    }

  public Map<Tag, String> getImageMeta(File image, Tag... tags)
          throws IllegalArgumentException, SecurityException, IOException {

    return getImageMeta(image, Format.NUMERIC, tags);
  }

  public Map<Tag, String> getImageMeta(File image, Format format, Tag... tags)
          throws IllegalArgumentException, SecurityException, IOException {

    String [] stringTags = new String[tags.length];
    int i=0;
    for (Tag tag : tags){
      stringTags[i++] = tag.getName();
    }
    return mapByTag(getImageMeta(image, format, true, stringTags));
  }

  public Map<String, String> getImageMeta(File image, Format format, TagGroup... tags)
          throws IllegalArgumentException, SecurityException, IOException {
    String [] stringTags = new String[tags.length];
    int i=0;
    for (TagGroup tag : tags){
      stringTags[i++] = tag.getValue();
    }
    return getImageMeta(image, format, false, stringTags);
  }

  private Map<String, String> getImageMeta(File image, Format format, boolean suppressDuplicates, String... tags)
              throws IllegalArgumentException, SecurityException, IOException {
		
    if (image == null){
			throw new IllegalArgumentException("image cannot be null and must be a valid stream of image data.");
    }
		if (format == null){
			throw new IllegalArgumentException("format cannot be null");
    }
		if (tags == null || tags.length == 0){
			throw new IllegalArgumentException("tags cannot be null and must contain 1 or more Tag to query the image for.");
    }
		if (!image.canRead()){
			throw new SecurityException(
					"Unable to read the given image ["
							+ image.getAbsolutePath()
							+ "], ensure that the image exists at the given path and that the executing Java process has permissions to read it.");
    }

		long startTime = System.currentTimeMillis();
		log.debug(String.format("Querying %d tags from image: %s", tags.length, image.getAbsolutePath()));

		long exifToolCallStartTime;

		/*
		 * Using ExifTool in daemon mode (-stay_open True) executes different
		 * code paths below. So establish the flag for this once and it is
		 * reused a multitude of times later in this method to figure out where
		 * to branch to.
		 */
		boolean stayOpen = featureSet.contains(Feature.STAY_OPEN);
    Map<String, String> resultMap;

		if (stayOpen) {
			log.debug("Using ExifTool in daemon mode (-stay_open True)...");

			// Always reset the cleanup task.
			resetCleanupTask();
      if (!isRunning()){
        startup();
      }

			log.debug("Streaming arguments to ExifTool process...");


      synchronized (this){
        boolean success = false;
        int attempts = 0;
        exifToolCallStartTime = System.currentTimeMillis();
        while (!success && attempts <= 2){
          attempts++;
          if (format == Format.NUMERIC){
            stream.writer.write("-n\n"); // numeric output
          }
          if (!suppressDuplicates){
            stream.writer.write("-a\n"); // Allow duplicate tags to be extracted
          }

          stream.writer.write("-S\n"); // compact output

          for (String tag : tags) {
            stream.writer.write('-');
            stream.writer.write(tag);
            stream.writer.write("\n");
          }

          stream.writer.write(image.getAbsolutePath());
          stream.writer.write("\n");

          log.debug("Executing ExifTool...");

          // Begin tracking the duration ExifTool takes to respond.
          exifToolCallStartTime = System.currentTimeMillis();

          // Run ExifTool on our file with all the given arguments.
          stream.writer.write("-execute\n");
          try {
            stream.writer.flush();
            success = true;
          } catch (IOException e){
            success = false;
            //only catch "Stream Closed" error (happens when process has died
            if (!e.getMessage().equals("Stream closed")){
              throw e; //
            }
            log.warn("Caught IOException / Stream closed, trying to restart daemon");
            stream = null;
            startup();
          }
        }
        resultMap = readResponse(stream, stayOpen);
      }

    //not in daemon mode, launch tool with each call
    } else {
      
			log.debug("Using ExifTool in non-daemon mode (-stay_open False)...");

			/*
			 * Since we are not using a stayOpen process, we need to setup the
			 * execution arguments completely each time.
			 */

      List<String> args = new ArrayList<String>(64);
			args.add(EXIF_TOOL_PATH);

			if (format == Format.NUMERIC){
				args.add("-n"); // numeric output
      }
			args.add("-S"); // compact output

      for (String tag : tags) {
        args.add("-" + tag);
      }

			args.add(image.getAbsolutePath());

			// Run the ExifTool with our args.
			stream = startExifToolProcess(args);

			// Begin tracking the duration ExifTool takes to respond.
			exifToolCallStartTime = System.currentTimeMillis();
      resultMap = readResponse(stream, stayOpen);
		}



    long exifToolElapsedTime = System.currentTimeMillis()-exifToolCallStartTime;
		// Print out how long the call to external ExifTool process took.
    if (log.isDebugEnabled()){
      log.debug(String.format("Finished reading ExifTool response in %d ms.", exifToolElapsedTime));
    }

		/*
		 * If we are not using a persistent ExifTool process, then after running
		 * the command above, the process exited in which case we need to clean
		 * our streams up since it no longer exists. If we were using a
		 * persistent ExifTool process, leave the streams open for future calls.
		 */
		if (!stayOpen){
			stream.close();
    }

    if (log.isDebugEnabled()){
      log.debug(String.format("Image Meta Processed in %d ms [queried %d tags and found %d values]",
              (System.currentTimeMillis() - startTime), tags.length,
              resultMap.size()));
    }

		return resultMap;
	}

  private Map<String, String> readResponse(IOStream stream, boolean stayOpen) throws IOException {

    log.debug("Reading response back from ExifTool...");
    Map<String, String> resultMap = new HashMap<String, String>(500);
    String line;

    while ((line = stream.reader.readLine()) != null) {
      String[] pair = TAG_VALUE_PATTERN.split(line, 2);

      if (pair.length == 2) {
        resultMap.put(pair[0], pair[1]);
        log.debug(String.format("\tRead Tag [name=%s, value=%s]", pair[0], pair[1]));
      } else {
          // Check for possible error messages when doing updates
          if(line.startsWith("Warning:")) {
              log.warn("ExifTool failed. {}", line);
              // Throw an exception since if logging is off the problem could silently fail
              throw new IOException("ExifTool failed. " + line);
          }
      }

      /*
       * When using a persistent ExifTool process, it terminates its
       * output to us with a "{ready}" clause on a new line, we need to
       * look for it and break from this loop when we see it otherwise
       * this process will hang indefinitely blocking on the input stream
       * with no data to read.
       */
      if (stayOpen && line.equals("{ready}")){
        break;
      }
    }
    return resultMap;
  }

  private static Map<Tag, String> mapByTag(Map<String,String> stringMap){
    Map<Tag, String> tagMap = new HashMap<Tag, String>(Tag.values().length);
    for (Tag tag : Tag.values()){
      if (stringMap.containsKey(tag.getName())){
        tagMap.put(tag, stringMap.get(tag.getName()));
      }
    }
    return tagMap;
  }

	/**
	 * Helper method used to make canceling the current task and scheduling a
	 * new one easier.
	 * <p/>
	 * It is annoying that we cannot just reset the timer on the task, but that
	 * isn't the way the java.util.Timer class was designed unfortunately.
	 */
	private void resetCleanupTask() {
		// no-op if the timer was never created.
		if (cleanupTimer == null){
			return;
    }

		log.debug("Resetting cleanup task...");

		// Cancel the current cleanup task if necessary.
		if (currentCleanupTask != null){
			currentCleanupTask.cancel();
    }

		// Schedule a new cleanup task.
		cleanupTimer.schedule(
				(currentCleanupTask = new CleanupTimerTask(this)),
				PROCESS_CLEANUP_DELAY, PROCESS_CLEANUP_DELAY);

		log.debug("\tSuccessful");
	}

	/**
	 * Class used to represent the {@link TimerTask} used by the internal auto
	 * cleanup {@link Timer} to call {@link ExifTool#close()} after a specified
	 * interval of inactivity.
	 * 
	 * @author Riyad Kalla (software@thebuzzmedia.com)
	 * @since 1.1
	 */
	private class CleanupTimerTask extends TimerTask {
		private ExifTool owner;

		public CleanupTimerTask(ExifTool owner) throws IllegalArgumentException {
			if (owner == null)
				throw new IllegalArgumentException(
						"owner cannot be null and must refer to the ExifTool instance creating this task.");

			this.owner = owner;
		}

		@Override
		public void run() {
			log.info("Auto cleanup task running...");
			owner.close();
		}
	}

	/**
	 * Class used to define an exception that occurs when the caller attempts to
	 * use a {@link Feature} that the underlying native ExifTool install does
	 * not support (i.e. the version isn't new enough).
	 * 
	 * @author Riyad Kalla (software@thebuzzmedia.com)
	 * @since 1.1
	 */
	public class UnsupportedFeatureException extends RuntimeException {
		private static final long serialVersionUID = -1332725983656030770L;

		public UnsupportedFeatureException(Feature feature) {
			super(
					"Use of feature ["
							+ feature.toString()
							+ "] requires version "
							+ feature.getVersion()
							+ " or higher of the native ExifTool program. The version of ExifTool referenced by the system property 'exiftool.path' is not high enough. You can either upgrade the install of ExifTool or avoid using this feature to workaround this exception.");
		}

	}
}