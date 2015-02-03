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

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.thebuzzmedia.exiftool.adapters.ExifToolService;

/*
 private def extractExifWithExifToolOld2(prefix: String, file: File): Try[Tags] =
 Try {

 def split(text: String): Pair[String, String] = {
 val all = text.splitAt(text.indexOf(":"))
 Pair(all._1.trim.replaceAll("[/ ]", ""), all._2.drop(1).trim)
 }
 //println("Coulnd't get exif info from " + file)
 import scala.sys.process._
 import scala.sys.process.ProcessIO
 val pb = Process(s"""exiftool "${file.getAbsolutePath}"""")
 var map = Map[String, String]()
 val pio = new ProcessIO(_ => (),
 stdout => scala.io.Source.fromInputStream(stdout)
 .getLines.foreach { x =>
 //println(s"found $x")
 map += split(x)
 },
 _ => ())
 val a = pb.run(pio)
 val blockTillExits = a.exitValue
 if (blockTillExits == 0) {
 //println(map)
 //"exiftool".!
 //println(map mkString "\n")
 val result = map.toMap.map { x =>
 //println(x)
 (prefix + x._1, formatted(x._2)_)
 }
 Tags(result)
 } else {
 throw new RuntimeException(s"Coulnd't get exif info from " + file + ". Got $blockTillExits from exiftool.")
 }
 }
 */
/**
 * Provide a Java-like interface to Phil Harvey's excellent, Perl-based <a
 * href="http://www.sno.phy.queensu.ca/~phil/exiftool">ExifToolNew3</a>.
 * <p/>
 * Initial work done by "Riyad Kalla" software@thebuzzmedia.com.
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
 * {@link #exifCmd}, using this class to communicate with ExifToolNew3 is as simple as creating an instance (
 * <code>ExifToolNew3 tool = new ExifToolNew3()</code>) and then making calls to
 * {@link #getImageMeta3(File, ReadOptions, Tag...)} or {@link #getImageMeta4(File, ReadOptions, Format, Tag...)} with a
 * list of {@link Tag}s you want to pull values for from the given image.
 * <p/>
 * In this default mode methods will automatically start an external ExifToolNew3 process to handle the request. After
 * ExifToolNew3 has parsed the tag values from the file, the external process exits and this class parses the result
 * before returning it to the caller.
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
 * The {@link Tag} Enum provides the {@link Tag#parseValue(String)} convenience method for parsing given
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
 * the version of ExifToolNew3 pointed at by {@link #exifCmd} before successfully instantiating the class and will
 * notify you via an {@link UnsupportedFeatureException} if the native ExifToolNew3 doesn't support the requested
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
 * The inactivity period can be controlled by modifying the {@link #processCleanupDelay} system variable. A value of
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
public class ExifToolNew3 implements RawExifTool {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExifToolNew3.class);

	private static final String ENV_EXIF_TOOL_PATH = "exiftool.path";
	private static final String ENV_EXIF_TOOL_PROCESSCLEANUPDELAY = "exiftool.processCleanupDelay";
	private static final long DEFAULT_PROCESS_CLEANUP_DELAY = 0;

	/**
	 * Name used to identify the (optional) cleanup {@link Thread}.
	 * <p/>
	 * This is only provided to make debugging and profiling easier for implementers making use of this class such that
	 * the resources this class creates and uses (i.e. Threads) are readily identifiable in a running VM.
	 * <p/>
	 * Default value is "<code>ExifToolNew3 Cleanup Thread</code>".
	 */
	static final String CLEANUP_THREAD_NAME = "ExifToolNew3 Cleanup Thread";

	/**
	 * Compiled {@link Pattern} of ": " used to split compact output from ExifToolNew3 evenly into name/value pairs.
	 */
	static final Pattern TAG_VALUE_PATTERN = Pattern.compile("\\s*:\\s*");
	static final String STREAM_CLOSED_MESSAGE = "Stream closed";

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
	private final String exifCmd;

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
	private final long processCleanupDelay;

	private final Map<Feature, Boolean> featureSupportedMap = new HashMap<Feature, Boolean>();
	private final Set<Feature> featureSet = EnumSet.noneOf(Feature.class);
	private final ReentrantLock lock = new ReentrantLock();
	private final VersionNumber exifVersion;
	private final Timer cleanupTimer;
	private TimerTask currentCleanupTask = null;
	private AtomicBoolean shuttingDown = new AtomicBoolean(false);
	private volatile ExifProcess process;
	private final Charset charset;
	/**
	 * Limits the amount of time (in mills) an exif operation can take. Setting value to greater than 0 to enable.
	 */
	private final int timeoutWhenKeepAlive;
	private static final int DEFAULT_TIMEOUT_WHEN_KEEP_ALIVE = 0;

	public ExifToolNew3() {
		this((Feature[]) null);
	}

	/**
	 * In this constructor, exifToolPath and processCleanupDelay are gotten from system properties exiftool.path and
	 * exiftool.processCleanupDelay. processCleanupDelay is optional. If not found, the default is used.
	 */
	public ExifToolNew3(Feature... features) {
		this(DEFAULT_TIMEOUT_WHEN_KEEP_ALIVE, features);
	}

	public ExifToolNew3(int timeoutWhenKeepAliveInMillis, Feature... features) {
		this(System.getProperty(ENV_EXIF_TOOL_PATH, "exiftool"), Long.getLong(ENV_EXIF_TOOL_PROCESSCLEANUPDELAY,
				DEFAULT_PROCESS_CLEANUP_DELAY), timeoutWhenKeepAliveInMillis, features);
	}

	public ExifToolNew3(String exifToolPath) {
		this(exifToolPath, DEFAULT_PROCESS_CLEANUP_DELAY, DEFAULT_TIMEOUT_WHEN_KEEP_ALIVE, (Feature[]) null);
	}

	public ExifToolNew3(String exifToolPath, Feature... features) {
		this(exifToolPath, DEFAULT_PROCESS_CLEANUP_DELAY, DEFAULT_TIMEOUT_WHEN_KEEP_ALIVE, features);
	}

	public ExifToolNew3(String exifCmd, long processCleanupDelay, int timeoutWhenKeepAliveInMillis, Feature... features) {
		this.exifCmd = exifCmd;
		this.processCleanupDelay = processCleanupDelay;
		this.exifVersion = ExifProcess.readVersion(exifCmd);
		this.timeoutWhenKeepAlive = timeoutWhenKeepAliveInMillis;
		if (features != null && features.length > 0) {
			for (Feature feature : features) {
				if (!feature.isSupported(exifVersion)) {
					throw new UnsupportedFeatureException(feature);
				}
				this.featureSet.add(feature);
				this.featureSupportedMap.put(feature, true);
			}
		}

		/*
		 * Now that initialization is done, init the cleanup timer if we are using STAY_OPEN and the delay time set is
		 * non-zero.
		 */
		if (isFeatureEnabled(Feature.STAY_OPEN)) {
			cleanupTimer = new Timer(CLEANUP_THREAD_NAME, true);
		} else {
			cleanupTimer = null;
		}
		charset = computeDefaultCharset(Arrays.asList(features));
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
	@Override
	public boolean isFeatureSupported(Feature feature) throws RuntimeException {
		if (feature == null) {
			throw new IllegalArgumentException("feature cannot be null");
		}

		Boolean supported = featureSupportedMap.get(feature);

		/*
		 * If there is no Boolean flag for the feature, support for it hasn't been checked yet with the native
		 * ExifToolNew3 install, so we need to do that.
		 */
		if (supported == null) {
			LOG.debug("Support for feature %s has not been checked yet, checking...");
			supported = feature.isSupported(exifVersion);
			featureSupportedMap.put(feature, supported);
		}

		return supported;
	}

	/**
	 * Used to startup the external ExifToolNew3 process and open the read/write streams used to communicate with it
	 * when {@link Feature#STAY_OPEN} is enabled. This method has no effect if the stay open feature is not enabled.
	 */
	// @Override
	// public void startup() {
	// if (featureSet.contains(Feature.STAY_OPEN)) {
	// shuttingDown.set(false);
	// ensureProcessRunning();
	// }
	// }

	private void ensureProcessRunning() {
		if (process == null || process.isClosed()) {
			synchronized (this) {
				if (process == null || process.isClosed()) {
					LOG.debug("Starting daemon ExifToolNew3 process and creating read/write streams (this only happens once)...");
					process = ExifProcess.startup(exifCmd, charset);
				}
			}
		}
		if (processCleanupDelay > 0) {
			synchronized (this) {
				if (currentCleanupTask != null) {
					currentCleanupTask.cancel();
					currentCleanupTask = null;
				}
				currentCleanupTask = new TimerTask() {
					@Override
					public void run() {
						LOG.info("Auto cleanup task running...");
						process.close();
					}
				};
				cleanupTimer.schedule(currentCleanupTask, processCleanupDelay);
			}
		}
	}

	/**
	 * This is same as {@link #close()}, added for consistency with {@link #startup()}
	 */
	@Override
	public void shutdown() {
		close();
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
	 * {@link #processCleanupDelay}.
	 * <p/>
	 * Calling this method on an instance of this class without {@link Feature#STAY_OPEN} support enabled has no effect.
	 */
	@Override
	public synchronized void close() {
		if (cleanupTimer != null) {
			cleanupTimer.cancel();
		}
		shuttingDown.set(true);
		if (process != null) {
			process.close();
		}
		if (currentCleanupTask != null) {
			currentCleanupTask.cancel();
			currentCleanupTask = null;
		}
	}

	@Override
	public boolean isStayOpen() {
		return featureSet.contains(Feature.STAY_OPEN);
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
	@Override
	public boolean isRunning() {
		return process != null && !process.isClosed();
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
	@Override
	public boolean isFeatureEnabled(Feature feature) throws IllegalArgumentException {
		if (feature == null) {
			throw new IllegalArgumentException("feature cannot be null");
		}
		return featureSet.contains(feature);
	}

	@Override
	public Map<String, String> getImageMeta(File file, ReadOptions readOptions, String... tags) throws IOException {
		// Validate input and create Arg Array
		final boolean stayOpen = featureSet.contains(Feature.STAY_OPEN);
		if (tags == null) {
			tags = new String[0];
		}
		List<String> args = new ArrayList<String>(tags.length + 4);
		if (readOptions == null) {
			throw new IllegalArgumentException("format cannot be null");
		} else if (readOptions.numericOutput) {
			args.add("-n"); // numeric output
		}
		if (readOptions.showDuplicates) {
			// args.add("-a");
			args.add("-duplicates"); // allow duplicates to be shown
		}
		// -S or -veryShort
		args.add("-veryShort"); // compact output
		for (String tag : tags) {
			args.add("-" + tag);
		}
		if (file == null) {
			throw new IllegalArgumentException("image cannot be null and must be a valid stream of image data.");
		}
		if (!file.canRead()) {
			throw new SecurityException(
					"Unable to read the given image ["
							+ file.getAbsolutePath()
							+ "], ensure that the image exists at the given path and that the executing Java process has permissions to read it.");
		}
		args.add(getAbsoluteFileName(file));

		// start process
		long startTime = System.currentTimeMillis();
		LOG.debug(String.format("Querying %d tags from image: %s", tags.length, file.getAbsolutePath()));
		LOG.info("call stayOpen=" + stayOpen + " exiftool " + Joiner.on(" ").join(args));
		/*
		 * Using ExifToolNew3 in daemon mode (-stay_open True) executes different code paths below. So establish the
		 * flag for this once and it is reused a multitude of times later in this method to figure out where to branch
		 * to.
		 */
		Map<String, String> resultMap;
		if (stayOpen) {
			LOG.debug("Using ExifToolNew3 in daemon mode (-stay_open True)...");
			resultMap = processStayOpen(args);
		} else {
			LOG.debug("Using ExifToolNew3 in non-daemon mode (-stay_open False)...");
			resultMap = ExifToolService.toMap(execute(args));
		}

		// Print out how long the call to external ExifToolNew3 process took.
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("Image Meta Processed in %d ms [queried %d tags and found %d values]",
					(System.currentTimeMillis() - startTime), tags.length, resultMap.size()));
		}

		return resultMap;
	}

	public String getAbsoluteFileName(File file) {
		if (!CharMatcher.ASCII.matchesAllOf(file.getAbsolutePath()) && featureSet.contains(Feature.WINDOWS))
			return getMSDOSName(file);
		else
			return file.getAbsolutePath();
	}

	/**
	 * There is a bug that prevents exiftool to read unicode file names. We can get the windows filename if necessary
	 * with getMSDOSName
	 * 
	 * @link(http://perlmaven.com/unicode-filename-support-suggested-solution)
	 * @link(http://stackoverflow.com/questions/18893284/how-to-get-short-filenames-in-windows-using-java)
	 */
	public static String getMSDOSName(File file) {
		try {
			String path = getAbsolutePath(file);
			Process process = Runtime.getRuntime().exec(
					"cmd /c for %I in (\"" + file.getAbsolutePath() + "\") do @echo %~fsI");
			process.waitFor();
			byte[] data = new byte[65536];
			int size = process.getInputStream().read(data);
			if (size > 0)
				path = new String(data, 0, size).replaceAll("\\r\\n", "");
			return path;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static String getAbsolutePath(File file) throws IOException {
		String path = file.getAbsolutePath();
		if (file.exists() == false)
			file = new File(path);
		path = file.getCanonicalPath();
		if (file.isDirectory() && (path.endsWith(File.separator) == false))
			path += File.separator;
		return path;
	}

	@Override
	public <T> void addImageMetadata(File image, Map<T, Object> values) throws IOException {
		// public void addImageMetadata(File image, Map<Tag, Object> values)
		// throws IOException {

		if (image == null) {
			throw new IllegalArgumentException("image cannot be null and must be a valid stream of image data.");
		}
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("values cannot be null and must contain 1 or more tag to value mappings");
		}

		if (!image.canWrite()) {
			throw new SecurityException(
					"Unable to write the given image ["
							+ image.getAbsolutePath()
							+ "], ensure that the image exists at the given path and that the executing Java process has permissions to write to it.");
		}

		LOG.info("Adding Tags {} to {}", values, image.getAbsolutePath());

		// start process
		long startTime = System.currentTimeMillis();

		execute(null, image, values);

		// Print out how long the call to external ExifToolNew3 process took.
		if (LOG.isDebugEnabled()) {
			LOG.debug(String.format("Image Meta Processed in %d ms [added %d tags]",
					(System.currentTimeMillis() - startTime), values.size()));
		}
	}

	private <T> void execute(WriteOptions options, File image, Map<T, Object> values) throws IOException {
		final boolean stayOpen = featureSet.contains(Feature.STAY_OPEN);
		if (stayOpen) {
			LOG.debug("Using ExifToolNew3 in daemon mode (-stay_open True)...");
			processStayOpen(createCommandList(image.getAbsolutePath(), values, stayOpen));
		} else {
			LOG.debug("Using ExifToolNew3 in non-daemon mode (-stay_open False)...");
			ExifProcess
					.executeToResults(exifCmd, createCommandList(image.getAbsolutePath(), values, stayOpen), charset);
		}
	}

	private <T> List<String> createCommandList(String filename, Map<T, Object> values, boolean stayOpen) {
		List<String> args = new ArrayList<String>(64);
		for (Map.Entry<T, Object> entry : values.entrySet()) {
			MetadataTag tag = (MetadataTag) entry.getKey();
			Object value = entry.getValue();
			args.addAll(toRawData(tag, value));
		}
		args.add(filename);
		return args;

	}

	private <T> List<String> toRawData(MetadataTag tag, Object value) {
		if (tag.getType().equals(String[].class)) {
			List<String> result = new LinkedList<String>();
			String[] array = (String[]) value;
			for (String value2 : array) {
				String raw = getRawExif(tag, value2);
				result.add(raw);
			}
			return result;
		} else {
			String raw = getRawExif(tag, value);
			return Lists.newArrayList(raw);
		}
	}

	private String getRawExif(MetadataTag tag, Object value) {
		StringBuilder arg = new StringBuilder();
		arg.append("-").append(tag.getKey());
		if (value instanceof Number) {
			arg.append("#");
		}
		arg.append("=");
		if (value != null) {
			// if (value instanceof String && !stayOpen) {
			// arg.append("\"").append(value.toString()).append("\"");
			// } else {
			arg.append(tag.toExif(value));
			// }
		}
		String raw = arg.toString();
		return raw;
	}

	/**
	 * Will attempt 3 times to use the running exif process, and if unable to complete successfully will throw
	 * IOException
	 */
	private Map<String, String> processStayOpen(List<String> args) throws IOException {
		int attempts = 0;
		while (attempts < 3 && !shuttingDown.get()) {
			attempts++;
			// make sure process is started
			ensureProcessRunning();
			TimerTask attemptTimer = null;
			try {
				if (timeoutWhenKeepAlive > 0) {
					attemptTimer = new TimerTask() {
						@Override
						public void run() {
							LOG.warn("Process ran too long closing, max " + timeoutWhenKeepAlive + " mills");
							process.close();
						}
					};
					cleanupTimer.schedule(attemptTimer, timeoutWhenKeepAlive);
				}
				LOG.debug("Streaming arguments to ExifToolNew3 process...");
				return ExifToolService.toMap(process.sendArgs(args));
			} catch (IOException ex) {
				if (STREAM_CLOSED_MESSAGE.equals(ex.getMessage()) && !shuttingDown.get()) {
					// only catch "Stream Closed" error (happens when process
					// has died)
					LOG.warn(String.format("Caught IOException(\"%s\"), will restart daemon", STREAM_CLOSED_MESSAGE));
					process.close();
				} else {
					throw ex;
				}
			} finally {
				if (attemptTimer != null)
					attemptTimer.cancel();
			}
		}
		if (shuttingDown.get()) {
			throw new IOException("Shutting Down");
		}
		throw new IOException("Ran out of attempts");
	}

	/**
	 * Helper method used to ensure a message is loggable before it is logged and then pre-pend a universal prefix to
	 * all LOG messages generated by this library to make the LOG entries easy to parse visually or programmatically.
	 * <p/>
	 * If a message cannot be logged (logging is disabled) then this method returns immediately.
	 * <p/>
	 * <strong>NOTE</strong>: Because Java will auto-box primitive arguments into Objects when building out the
	 * <code>params</code> array, care should be taken not to call this method with primitive values unless
	 * {@link #DEBUG} is <code>true</code>; otherwise the VM will be spending time performing unnecessary auto-boxing
	 * calculations.
	 * 
	 * @param message
	 *            The LOG message in <a href=
	 *            "http://download.oracle.com/javase/6/docs/api/java/util/Formatter.html#syntax" >format string
	 *            syntax</a> that will be logged.
	 * @param params
	 *            The parameters that will be swapped into all the place holders in the original messages before being
	 *            logged.
	 * 
	 * @see #LOG_PREFIX
	 */
	protected static void log(String message, Object... params) {
		LOG.debug(message, params);
	}

	@Override
	public void rebuildMetadata(File file) throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public void rebuildMetadata(WriteOptions options, File file) throws IOException {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public <T> void writeMetadata(WriteOptions options, File image, Map<T, Object> values) throws IOException {
		addImageMetadata(image, values);
		// throw new RuntimeException("Not implemented.");
	}

	@Override
	protected void finalize() throws Throwable {
		LOG.debug("Shutdown on finalize ...");
		shutdown();
		super.finalize();
	}

	public static Charset computeDefaultCharset(Collection<Feature> features) {
		if (features.contains(Feature.WINDOWS))
			return Charset.forName("windows-1252");
		return Charset.defaultCharset();
	}

	@Override
	public List<String> execute(List<String> args) {
		try {
			return ExifProcess.executeToResults(exifCmd, args, charset);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}