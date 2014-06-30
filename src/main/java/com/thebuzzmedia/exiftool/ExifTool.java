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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Provide a Java-like interface to Phil Harvey's excellent,
 * Perl-based <a href="http://www.sno.phy.queensu.ca/~phil/exiftool">ExifTool</a>.
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
 * {@link #readMetadata(ReadOptions,java.io.File, Object...)} (optionally supplying tags or
 * {@link #writeMetadata(WriteOptions,java.io.File, java.util.Map)}
 * <p/>
 * In this default mode methods will automatically
 * start an external ExifTool process to handle the request. After ExifTool has
 * parsed the tag values from the file, the external process exits and this
 * class parses the result before returning it to the caller.
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
 * by {@link #ENV_EXIF_TOOL_PATH} before successfully instantiating the class and
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
 * {@link #ENV_EXIF_TOOL_PROCESSCLEANUPDELAY} system variable. A value of <code>0</code> or
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
  public static final String ENV_EXIF_TOOL_PATH = "exiftool.path";
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
  private static final String STREAM_CLOSED_MESSAGE = "Stream closed";
  private static final String EXIF_DATE_FORMAT = "yyyy:MM:dd HH:mm:ss";

  private static Logger log = LoggerFactory.getLogger(ExifTool.class);

  private final Map<Feature, Boolean> featureSupportedMap = new HashMap<Feature, Boolean>();
  private final Set<Feature> featureEnabledSet = EnumSet.noneOf(Feature.class);
  private ReadOptions defReadOptions = new ReadOptions();
  private WriteOptions defWriteOptions = new WriteOptions();
  private final VersionNumber exifVersion;
  private final ExifProxy exifProxy;

  public ExifTool(){
    this((Feature[]) null);
  }

  /**
   * In this constructor, exifToolPath and processCleanupDelay are read from system properties
   * exiftool.path and exiftool.processCleanupDelay. processCleanupDelay is optional. If not found,
   * the default is used.
   */
  public ExifTool (Feature ... features){
    this(
      System.getProperty(ENV_EXIF_TOOL_PATH, "exiftool"),
      Long.getLong(ENV_EXIF_TOOL_PROCESSCLEANUPDELAY, DEFAULT_PROCESS_CLEANUP_DELAY),
      features
    );
  }

  /**
   * Pass in the absolute path to the ExifTool executable on the host system.
   */
  public ExifTool(String exifToolPath) {
    this(exifToolPath, DEFAULT_PROCESS_CLEANUP_DELAY);
  }

  public ExifTool(String exifToolPath, Feature ... features) {
    this(exifToolPath, DEFAULT_PROCESS_CLEANUP_DELAY, features);
  }

  public ExifTool(String exifCmd, long processCleanupDelay, Feature ... features) {
    this.exifVersion = ExifProcess.readVersion(exifCmd);
    if (features != null && features.length > 0) {
      for (Feature feature : features) {
        if ( ! feature.isSupported(exifVersion) ) {
          throw new UnsupportedFeatureException(feature);
        }
        this.featureEnabledSet.add(feature);
        this.featureSupportedMap.put(feature,true);
      }
    }

    List<String> baseArgs = new ArrayList<String>(3);
    if (featureEnabledSet.contains(Feature.MWG_MODULE) )  {
      baseArgs.addAll(Arrays.asList("-use","MWG"));
    }
    if (featureEnabledSet.contains(Feature.STAY_OPEN) ) {
      KeepAliveExifProxy proxy = new KeepAliveExifProxy(exifCmd,baseArgs);
      proxy.setInactiveTimeout(processCleanupDelay);
      exifProxy = proxy;
    } else {
      exifProxy = new SingleUseExifProxy(exifCmd,baseArgs);
    }
  }

  /**
   * Limits the amount of time (in mills) an exif operation can take. Setting value to greater than 0 to enable.
   */
  public ExifTool setRunTimeout(long mills) {
    defReadOptions  = defReadOptions.withRunTimeoutMills(mills);
    defWriteOptions = defWriteOptions.withRunTimeoutMills(mills);
    return this;
  }

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
    if (feature == null){
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
      featureSupportedMap.put(feature,supported);
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
  public boolean isFeatureEnabled(Feature feature) throws IllegalArgumentException {
    if (feature == null){
      throw new IllegalArgumentException("feature cannot be null");
    }
    return featureEnabledSet.contains(feature);
  }

  /**
   * Used to startup the external ExifTool process and open the read/write
   * streams used to communicate with it when {@link Feature#STAY_OPEN} is
   * enabled. This method has no effect if the stay open feature is not enabled.
   */
  public void startup(){
    exifProxy.startup();
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
    return exifProxy != null && ! exifProxy.isRunning();
  }


  public ReadOptions getReadOptions() {
    return defReadOptions;
  }

  public ExifTool setReadOptions(ReadOptions options) {
    defReadOptions = options;
    return this;
  }

  public WriteOptions getWriteOptions() {
    return defWriteOptions;
  }

  public ExifTool setWriteOptions(WriteOptions options) {
    defWriteOptions = options;
    return this;
  }
  //================================================================================
  //OLD API
  public static enum Format{NUMERIC, HUMAN_READABLE}

  public Map<Tag, String> getImageMeta(File image, Tag... tags)
    throws IllegalArgumentException, SecurityException, IOException {
    return getImageMeta(image, Format.NUMERIC, tags);
  }

  public Map<Tag, String> getImageMeta(File image, Format format, Tag... tags)
    throws IllegalArgumentException, SecurityException, IOException {

    String [] stringTags = new String[tags.length];
    int i=0;
    for (Tag tag : tags){
      stringTags[i++] = tag.getKey();
    }
    Map<String,String> result = getImageMeta(image, format, true, stringTags);
    return Tag.toTagMap(result);
  }

  public Map<String, String> getImageMeta(File image, Format format, TagGroup... tags)
    throws IllegalArgumentException, SecurityException, IOException {
    String [] stringTags = new String[tags.length];
    int i=0;
    for (TagGroup tag : tags){
      stringTags[i++] = tag.getKey();
    }
    return getImageMeta(image, format, false, stringTags);
  }

  public Map<String,String> getImageMeta(File file, Format format, boolean supressDuplicates, String... tags) throws IOException {
    ReadOptions options = defReadOptions.withNumericOutput(format == Format.NUMERIC).withShowDuplicates(!supressDuplicates).withConvertTypes(false);
    Map<Object,Object> result = readMetadata(options, file, tags);
    Map<String,String> data = new TreeMap<String,String>();
    for(Map.Entry<Object,Object> entry : result.entrySet()) {
      data.put(entry.getKey().toString(), entry.getValue()!=null?entry.getValue().toString():"");
    }
    return data;
  }

  public <T> void addImageMetadata(File image, Map<T, Object> values) throws IOException {
    writeMetadata(defWriteOptions.withDeleteBackupFile(false),image,values);
  }
  //================================================================================
  public Map<Object,Object> readMetadata(File file, Object... tags) throws IOException {
    return readMetadata(defReadOptions,file,tags);
  }
  /**
   * Reads metadata from the file.
   */
  public Map<Object,Object> readMetadata(ReadOptions options, File file, Object... tags) throws IOException {
    if (file == null){
      throw new IllegalArgumentException("file cannot be null and must be a valid stream of image data.");
    }
    if (!file.canRead()){
      throw new SecurityException("Unable to read the given image ["+file.getAbsolutePath()
        + "], ensure that the image exists at the given path and that the executing Java process has permissions to read it.");
    }

    List<String> args = new ArrayList<String>(tags.length+2);
    if ( options.numericOutput ) {
      args.add("-n"); // numeric output
    }
    if ( options.showDuplicates) {
      args.add("-a");
    }
    if ( ! options.showEmptyTags ) {
      args.add("-S"); // compact output
    }
    for(Object tag: tags) {
      if ( tag instanceof MetadataTag ) {
        args.add("-"+((MetadataTag) tag).getKey());
      } else {
        args.add("-" + tag);
      }
    }
    args.add(file.getAbsolutePath());

    Map<String,String> resultMap = exifProxy.execute(options.runTimeoutMills,args);

    Map<Object,Object> metadata = new HashMap<Object,Object>(resultMap.size());

    for(Object tag: tags) {
      MetadataTag metaTag;
      if (tag instanceof MetadataTag) {
        metaTag = (MetadataTag) tag;
      } else {
        metaTag = toTag(tag.toString());
      }
      if ( metaTag.isMapped() ) {
        String input = resultMap.remove(metaTag.getKey());
        if ( ! options.showEmptyTags && (input == null || input.isEmpty())  ) {
          continue;
        }
        Object value = options.convertTypes ? deserialize(metaTag.getKey(), input, metaTag.getType()) : input;
        //maps with tag passed in, as caller expects to fetch
        metadata.put(tag, value);
      }
    }
    for(Map.Entry<String,String> entry : resultMap.entrySet()) {
      if ( ! options.showEmptyTags && entry.getValue() == null || entry.getValue().isEmpty() ) {
        continue;
      }
      if ( options.convertTypes) {
        MetadataTag metaTag = toTag(entry.getKey());
        Object value = deserialize(metaTag.getKey(), entry.getValue(), metaTag.getType());
        metadata.put(entry.getKey(), value);
      } else {
        metadata.put(entry.getKey(),entry.getValue());

      }
    }
    return metadata;
  }

  public <T> void writeMetadata(File image, Map<T, Object> values) throws IOException {
    writeMetadata(defWriteOptions, image, values);
  }
  /**
   * Takes a map of tags (either (@link Tag) or Strings for keys) and replaces/appends them to the metadata.
   */
  public <T> void writeMetadata(WriteOptions options, File image, Map<T, Object> values) throws IOException {
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

    List<String> args = new ArrayList<String>(values.size()+3);
    for(Map.Entry<?, Object> entry : values.entrySet()) {
      args.addAll(serializeToArgs(entry.getKey(),entry.getValue()));
    }
    args.add(image.getAbsolutePath());

    //start process
    long startTime = System.currentTimeMillis();
    try {
      exifProxy.execute(options.runTimeoutMills, args);
    } finally {
      if ( options.deleteBackupFile ) {
        File origBackup = new File(image.getAbsolutePath()+"_original");
        if ( origBackup.exists() ) origBackup.delete();
      }
    }

    // Print out how long the call to external ExifTool process took.
    if (log.isDebugEnabled()){
      log.debug(String.format("Image Meta Processed in %d ms [added %d tags]",
        (System.currentTimeMillis() - startTime), values.size()));
    }
  }
  //================================================================================
  //STATIC helpers

  static List<String> serializeToArgs(Object tag, Object value) {
    final Class tagType;
    final String tagName;
    if ( tag instanceof MetadataTag ) {
      tagName = ((MetadataTag) tag).getKey();
      tagType = ((MetadataTag) tag).getType();
    } else {
      tagName = tag.toString();
      tagType = null;
    }

    //pre process
    if ( value != null ) {
      if ( value.getClass().isArray() ) {
        //convert array to iterable, this is lame
        int len = Array.getLength(value);
        List<Object> newList = new ArrayList<Object>(len);
        for (int i = 0; i < len; i++) {
          Object item = Array.get(value,i);
          newList.add(item);
        }
        value = newList;
      } else if ( value instanceof Number && Date.class.equals(tagType) ) {
        //if we know this is a date field and data is a number assume it is unix epoch time
        Date date = new Date(((Number) value).longValue());
        value = date;
      }
    }

    List<String> args = new ArrayList<String>(4);
    String arg;
    if (value == null ) {
      arg = String.format("-%s=",tagName);
    } else if ( value instanceof Number ) {
      arg = String.format("-%s#=%s",tagName,value);
    } else if(value instanceof Date) {
      SimpleDateFormat formatter = new SimpleDateFormat(EXIF_DATE_FORMAT);
      arg = String.format("-%s=%s",tagName,formatter.format((Date)value));
    } else if (value instanceof Iterable) {
      Iterable it = (Iterable) value;
      args.add("-sep");
      args.add(",");
      StringBuilder itemList = new StringBuilder();
      for(Object item : it) {
        if ( itemList.length() > 0 ) {
          itemList.append(",");
        }
        itemList.append(item);
      }
      arg = String.format("-%s=%s",tagName, itemList);
    } else {
      if ( tagType != null && tagType.isArray() ) {
        args.add("-sep");
        args.add(",");
      }
      arg = String.format("-%s=%s",tagName,value);
    }
    args.add(arg);
    return args;
  }

  static Object deserialize(String tagName, String value, Class expectedType) {
    try {
      if ( Boolean.class.equals(expectedType) ) {
        if ( value == null ) return null;
        value = value.trim().toLowerCase();
        switch (value.charAt(0)) {
          case 'n': case 'f': case '0': return false;
        }
        if ( value.equals("off") ) {
          return false;
        }
        return true;
      } else if ( Date.class.equals(expectedType) ) {
        if ( value == null ) return null;
        SimpleDateFormat formatter = new SimpleDateFormat(EXIF_DATE_FORMAT);
        return formatter.parse(value);
      } else if ( Integer.class.equals(expectedType) ) {
        if ( value == null ) return 0;
        return Integer.parseInt(value);
      } else if ( Long.class.equals(expectedType) ) {
        if ( value == null ) return 0;
        return Long.parseLong(value);
      } else if ( Float.class.equals(expectedType) ) {
        if ( value == null ) return 0;
        return Float.parseFloat(value);
      } else if ( Double.class.equals(expectedType) ) {
        if ( value == null ) return 0;
        String[] enumeratorAndDivisor = value.split("/");
        if ( enumeratorAndDivisor.length == 2 ) {
          return Double.parseDouble(enumeratorAndDivisor[0]) / Double.parseDouble(enumeratorAndDivisor[1]);
        } else {
          return Double.parseDouble(value);
        }
      } else if ( String[].class.equals(expectedType) ) {
        if ( value == null ) return new String[0];
        return value.split(",");
      } else {
        return value;
      }
    } catch (ParseException ex) {
      log.warn("Invalid format, Tag:"+tagName);
      return null;
    } catch (NumberFormatException ex) {
      log.warn("Invalid format, Tag:"+tagName);
      return null;
    }

  }

  static MetadataTag toTag(String name) {
    for(Tag tag: Tag.values()) {
      if ( tag.getKey().equalsIgnoreCase(name) ) {
        return tag;
      }
    }
    for(MwgTag tag: MwgTag.values()) {
      if ( tag.getKey().equalsIgnoreCase(name) ) {
        return tag;
      }
    }
    return new CustomTag(name,String.class);
  }

  //================================================================================
  /**
   * Represents an external exif process.  Works for both single use and keep alive modes.
   * This is the actual process, with streams for reading and writing data.
   */
  public static final class ExifProcess {
    public static VersionNumber readVersion(String exifCmd) {
      ExifProcess process = new ExifProcess(false, Arrays.asList(exifCmd, "-ver"));
      try {
        return new VersionNumber(process.readLine());
      } catch (IOException ex) {
        throw new RuntimeException(String.format("Unable to check version number of ExifTool: %s", exifCmd));
      } finally {
        process.close();
      }
    }

    private final ReentrantLock closeLock = new ReentrantLock(false);
    private final boolean keepAlive;
    private final Process process;
    private final BufferedReader reader;
    private final OutputStreamWriter writer;
    private volatile boolean closed = false;

    public ExifProcess(boolean keepAlive, List<String> args) {
      this.keepAlive = keepAlive;
      log.debug(String.format("Attempting to start ExifTool process using args: %s", args));
      try {
        this.process = new ProcessBuilder(args).start();
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new OutputStreamWriter(process.getOutputStream());
        log.debug("\tSuccessful");
      } catch (Exception e) {
        String message = "Unable to start external ExifTool process using the execution arguments: "
          + args
          + ". Ensure ExifTool is installed correctly and runs using the command path '"
          + args.get(0)
          + "' as specified by the 'exiftool.path' system property.";

        log.debug(message);
        throw new RuntimeException(message, e);
      }
    }

    public synchronized Map<String, String> sendToRunning(List<String> args) throws IOException {
      if (!keepAlive) {
        throw new IOException("Not KeepAlive Process");
      }
      StringBuilder builder = new StringBuilder();
      for (String arg : args) {
        builder.append(arg).append("\n");
      }
      builder.append("-execute\n");
      writeFlush(builder.toString());
      return readResponse();
    }

    public synchronized void writeFlush(String message) throws IOException {
      if (closed) throw new IOException(STREAM_CLOSED_MESSAGE);
      writer.write(message);
      writer.flush();
    }

    public synchronized String readLine() throws IOException {
      if (closed) throw new IOException(STREAM_CLOSED_MESSAGE);
      return reader.readLine();
    }

    public synchronized Map<String, String> readResponse() throws IOException {
      if (closed) throw new IOException(STREAM_CLOSED_MESSAGE);
      log.debug("Reading response back from ExifTool...");
      Map<String, String> resultMap = new HashMap<String, String>(500);
      String line;

      while ((line = reader.readLine()) != null) {
        if (closed) throw new IOException(STREAM_CLOSED_MESSAGE);
        String[] pair = TAG_VALUE_PATTERN.split(line, 2);

        if (pair.length == 2) {
          resultMap.put(pair[0], pair[1]);
          log.debug(String.format("\tRead Tag [name=%s, value=%s]", pair[0], pair[1]));
        }

      /*
       * When using a persistent ExifTool process, it terminates its
       * output to us with a "{ready}" clause on a new line, we need to
       * look for it and break from this loop when we see it otherwise
       * this process will hang indefinitely blocking on the input stream
       * with no data to read.
       */
        if (keepAlive && line.equals("{ready}")) {
          break;
        }
      }
      return resultMap;
    }

    public boolean isClosed() {
      return closed;
    }

    public void close() {
      if (!closed) {
        closeLock.lock();
        try {
          if (!closed) {
            closed = true;
            log.debug("Attempting to close ExifTool daemon process, issuing '-stay_open\\nFalse\\n' command...");
            try {
              writer.write("-stay_open\nFalse\n");
              writer.flush();
            } catch (IOException ex) {
              //log.error(ex,ex);
            }
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

            log.debug("Read/Write streams successfully closed.");

            try {
              process.destroy();
            } catch (Exception e) {
              //
            }
            //process = null;

          }
        } finally {
          closeLock.unlock();
        }
      }
    }
  }

  /**
   * A Proxy to an Exif Process, will restart if backing exif process died, or run new one on every call.
   * @author Matt Gile, msgile
   */
  public interface ExifProxy {
    public void startup();
    public Map<String, String> execute(long runTimeoutMills, List<String> args) throws IOException;
    public boolean isRunning();
    public void shutdown();
  }

  /**
   * Manages an external exif process in keep alive mode.
   */
  public static class KeepAliveExifProxy implements ExifProxy {
    private final List<String> startupArgs;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final Timer cleanupTimer = new Timer(CLEANUP_THREAD_NAME, true);
    private long inactivityTimeout = 0;
    private volatile long lastRunStart = 0;
    private volatile ExifProcess process;

    public KeepAliveExifProxy(String exifCmd, List<String> baseArgs) {
      inactivityTimeout = Long.getLong(ENV_EXIF_TOOL_PROCESSCLEANUPDELAY, DEFAULT_PROCESS_CLEANUP_DELAY);
      startupArgs = new ArrayList<String>(baseArgs.size()+5);
      startupArgs.add(exifCmd);
      startupArgs.addAll(Arrays.asList("-stay_open", "True"));
      startupArgs.addAll(baseArgs);
      startupArgs.addAll(Arrays.asList("-@", "-"));
      //runs every minute to check if process has been inactive too long
      cleanupTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          if ( process != null && lastRunStart > 0 && inactivityTimeout > 0 ) {
            if ( (System.currentTimeMillis()-lastRunStart) > inactivityTimeout) {
              synchronized (this) {
                if ( process != null ) {
                  process.close();
                }
              }
            }
          }
        }
      },60*1000); //every minute
    }

    public void setInactiveTimeout(long mills) {
      this.inactivityTimeout = mills;
    }

    public void startup() {
      shuttingDown.set(false);
      if (process ==null || process.isClosed()) {
        synchronized (this){
          if (process ==null || process.isClosed() ){
            log.debug("Starting daemon ExifTool process and creating read/write streams (this only happens once)...");
            process = new ExifProcess(true, startupArgs);
          }
        }
      }
    }

    public Map<String,String> execute(final long runTimeoutMills, List<String> args) throws IOException {
      lastRunStart = System.currentTimeMillis();
      int attempts = 0;
      while (attempts < 3 && ! shuttingDown.get()){
        attempts++;
        if (process ==null || process.isClosed()) {
          synchronized (this){
            if (process ==null || process.isClosed() ){
              log.debug("Starting daemon ExifTool process and creating read/write streams (this only happens once)...");
              process = new ExifProcess(true, startupArgs);
            }
          }
        }
        TimerTask attemptTimer = null;
        try {
          if ( runTimeoutMills > 0 ) {
            attemptTimer = new TimerTask() {
              public void run() {
                if (process != null && ! process.isClosed()) {
                  log.warn("Process ran too long closing, max " + runTimeoutMills + " mills");
                  process.close();
                }
              }
            };
            cleanupTimer.schedule(attemptTimer, runTimeoutMills);
          }
          log.debug("Streaming arguments to ExifTool process...");
          return process.sendToRunning(args);
        } catch (IOException ex){
          if ( STREAM_CLOSED_MESSAGE.equals(ex.getMessage()) && ! shuttingDown.get() ){
            //only catch "Stream Closed" error (happens when process has died)
            log.warn(String.format("Caught IOException(\"%s\"), will restart daemon",STREAM_CLOSED_MESSAGE));
            process.close();
          } else {
            throw ex;
          }
        } finally {
          if ( attemptTimer != null ) attemptTimer.cancel();
        }
      }
      if ( shuttingDown.get() ) {
        throw new IOException("Shutting Down");
      }
      throw new IOException("Ran out of attempts");
    }

    public boolean isRunning() {
      return process != null && ! process.isClosed();
    }

    public void shutdown() {
      shuttingDown.set(true);
    }
  }

  public static class SingleUseExifProxy implements ExifProxy {
    private final Timer cleanupTimer = new Timer(CLEANUP_THREAD_NAME, true);
    private final List<String> baseArgs;

    public SingleUseExifProxy(String exifCmd, List<String> defaultArgs) {
      this.baseArgs = new ArrayList<String>(defaultArgs.size()+1);
      this.baseArgs.add(exifCmd);
      this.baseArgs.addAll(defaultArgs);
    }

    public Map<String, String> execute(final long runTimeoutMills, List<String> args) throws IOException {
      List<String> newArgs = new ArrayList<String>(baseArgs.size()+args.size());
      newArgs.addAll(baseArgs);
      newArgs.addAll(args);
      final ExifProcess process = new ExifProcess(false, newArgs);
      TimerTask attemptTimer = null;
      if ( runTimeoutMills > 0 ) {
        attemptTimer = new TimerTask() {
          public void run() {
            if ( ! process.isClosed() ) {
              log.warn("Process ran too long closing, max " + runTimeoutMills + " mills");
              process.close();
            }
          }
        };
        cleanupTimer.schedule(attemptTimer, runTimeoutMills);
      }
      try {
        return process.readResponse();
      } finally {
        process.close();
        if ( attemptTimer != null ) attemptTimer.cancel();
      }
    }

    public void startup() {
      ;
    }

    public boolean isRunning() {
      return false;
    }

    public void shutdown() {
      ;
    }
  }
  //================================================================================
  /**
   * All the read options, is immutable, copy on change, fluent style "with" setters.
   */
  public static class ReadOptions {
    private final long runTimeoutMills;
    private final boolean convertTypes;
    private final boolean numericOutput;
    private final boolean showDuplicates;
    private final boolean showEmptyTags;

    public ReadOptions() {
      this(0,false,false,false,false);
    }

    private ReadOptions(long runTimeoutMills, boolean convertTypes, boolean numericOutput, boolean showDuplicates, boolean showEmptyTags) {
      this.runTimeoutMills = runTimeoutMills;
      this.convertTypes = convertTypes;
      this.numericOutput = numericOutput;
      this.showDuplicates = showDuplicates;
      this.showEmptyTags = showEmptyTags;
    }

    public String toString() {
      return String.format("%s(runTimeout:%,d convertTypes:%s showDuplicates:%s showEmptyTags:%s)",
        getClass().getSimpleName(),runTimeoutMills,convertTypes,showDuplicates,showEmptyTags);
    }

    /**
     * Sets the maximum time a process can run
     */
    public ReadOptions withRunTimeoutMills(long mills) {
      return new ReadOptions(mills,convertTypes,numericOutput, showDuplicates,showEmptyTags);
    }

    /**
     * By default all values will be returned as the strings printed by the exiftool.
     * If this is enabled then {@link MetadataTag#getType()} is used to cast the string into a java type.
     */
    public ReadOptions withConvertTypes(boolean enabled) {
      return new ReadOptions(runTimeoutMills,enabled,numericOutput, showDuplicates,showEmptyTags);
    }

    /**
     * Setting this to true will add the "-n" option causing the ExifTool to output of some tags to change.
     * <p/>
     * ExifTool, via the <code>-n</code> command line arg, is capable of
     * returning most values in their raw numeric form (e.g.
     * Aperture="2.8010323841") as well as a more human-readable/friendly format
     * (e.g. Aperture="2.8").
     * <p/>
     * While the {@link Tag}s defined on this class do provide a hint at the
     * type of the result (see {@link MetadataTag#getType()}), that hint only applies
     * when the numeric value is returned.
     * <p/>
     * If the caller finds the human-readable format easier to process,
     * Set this to false, the default.
     * <p/>
     * In order to see the types of values that are returned when human readable
     * is used (default), you can check the comprehensive <a
     * href="http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/index.html">
     * ExifTool Tag Guide</a>.
     * <p/>
     * This makes sense with some values like Aperture that in numeric
     * format end up returning as 14-decimal-place, high
     * precision values that are near the intended value (e.g.
     * "2.79999992203711" instead of just returning "2.8"). On the other hand,
     * other values (like Orientation) are easier to parse when their numeric
     * value (1-8) is returned instead of a much longer friendly name (e.g.
     * "Mirror horizontal and rotate 270 CW").
     *
     * Attempted from work done by
     * @author Riyad Kalla (software@thebuzzmedia.com)
     */
    public ReadOptions withNumericOutput(boolean enabled) {
      return new ReadOptions(runTimeoutMills,convertTypes,enabled, showDuplicates,showEmptyTags);
    }

    /**
     * If enabled will show tags which are duplicated between different tag regions, relates to the "-a" option in ExifTool.
     */
    public ReadOptions withShowDuplicates(boolean enabled) {
      return new ReadOptions(runTimeoutMills,convertTypes,numericOutput,enabled,showEmptyTags);
    }

    public ReadOptions withShowEmptyTags(boolean enabled) {
      return new ReadOptions(runTimeoutMills,convertTypes,numericOutput,showDuplicates,enabled);
    }
  }

  //================================================================================
  /**
   * All the write options, is immutable, copy on change, fluent style "with" setters.
   */
  public static class WriteOptions {
    private final long runTimeoutMills;
    private final boolean deleteBackupFile;

    public WriteOptions() {
      this(0,false);
    }

    private WriteOptions(long runTimeoutMills, boolean deleteBackupFile) {
      this.runTimeoutMills = runTimeoutMills;
      this.deleteBackupFile = deleteBackupFile;
    }

    public String toString() {
      return String.format("%s(runTimeOut:%,d deleteBackupFile:%s)",getClass().getSimpleName(),runTimeoutMills,deleteBackupFile);
    }

    public WriteOptions withRunTimeoutMills(long mills) {
      return new WriteOptions(mills,deleteBackupFile);
    }
    /**
     * ExifTool automatically makes a backup copy a file before writing metadata tags in the form
     * "file.ext_original", by default this tool will delete that original file after the writing is done.
     */
    public WriteOptions withDeleteBackupFile(boolean enabled) {
      return new WriteOptions(runTimeoutMills,enabled);
    }
  }

  //================================================================================
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
    STAY_OPEN(8,36),
    /**
     * Acitves the MWG modules. The Metadata Working Group (MWG) recommends
     * techniques to allow certain overlapping EXIF, IPTC and XMP tags to be
     * reconciled when reading, and synchronized when writing. The MWG Composite tags
     * below are designed to aid in the implementation of these recommendations.
     * Will add the args " -use MWG"
     *
     * @see <a href="http://www.sno.phy.queensu.ca/~phil/exiftool/TagNames/MWG.html">ExifTool MWG Docs</a>
     * !! Note these version numbers are not correct
     */
    MWG_MODULE(8,36),
    ;


    private VersionNumber requireVersion;
    private Feature(int... numbers) {
      this.requireVersion = new VersionNumber(numbers);
    }
    /**
     * Used to get the version of ExifTool required by this feature in order
     * to work.
     *
     * @return the version of ExifTool required by this feature in order to
     *         work.
     */
    VersionNumber getVersion() {
      return requireVersion;
    }

    boolean isSupported(VersionNumber exifVersionNumber) {
      return requireVersion.isBeforeOrEqualTo(exifVersionNumber);
    }
  }

  //================================================================================
  /**
   * Version Number used to determine if one version is after another.
   * @author Matt Gile, msgile
   */
  static class VersionNumber {
    private final int[] numbers;

    public VersionNumber(String str) {
      String[] versionParts =  str.trim().split("\\.");
      this.numbers = new int[versionParts.length];
      for(int i=0; i<versionParts.length; i++) {
        numbers[i] = Integer.parseInt(versionParts[i]);
      }
    }

    public VersionNumber(int... numbers) {
      this.numbers = numbers;
    }

    public boolean isBeforeOrEqualTo(VersionNumber other) {
      int max = Math.min(this.numbers.length, other.numbers.length);
      for(int i=0; i<max; i++) {
        if ( this.numbers[i] >  other.numbers[i] ) {
          return false;
        } else  if ( this.numbers[i] < other.numbers[i] ) {
          return true;
        }
      }
      //assume missing number as zero, so if the current process number is more digits it is a higher version
      return this.numbers.length <= other.numbers.length;
    }

    public String toString() {
      StringBuilder builder = new StringBuilder();
      for (int number : numbers) {
        if (builder.length() > 0) {
          builder.append(".");
        }
        builder.append(number);
      }
      return builder.toString();
    }
  }


  //================================================================================
  /**
   * Base type for all "tag" passed to exiftool.
   * The key is the value passed to the exiftool like "-creator".
   * The Types is used for automatic type conversions.
   *
   */
  public interface MetadataTag {
    /**
     * Returns the values passed to exiftool
     */
    public String getKey();
    /**
     * The types
     */
    public Class getType();
    public boolean isMapped();
  }

  //================================================================================
  /**
   * Enum used to pre-define a convenient list of tags that can be easily
   * extracted from images using this class with an external install of
   * ExifTool.
   * <p/>
   * Each tag defined also includes a type hint for the parsed value
   * associated with it when the default humanReadable is false.
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
  public enum Tag implements MetadataTag {
    //single entry tags
    APERTURE("ApertureValue", Double.class),
    AUTHOR("XPAuthor", String.class),
    COLOR_SPACE("ColorSpace", Integer.class),
    COMMENT("XPComment", String.class),
    CONTRAST("Contrast", Integer.class),
    CREATE_DATE("CreateDate", Date.class),
    CREATION_DATE("CreationDate", Date.class),
    DATE_CREATED("DateCreated", Date.class),
    DATE_TIME_ORIGINAL("DateTimeOriginal", Date.class),
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
        if (tag.getKey().equals(name)){
          return tag;
        }
      }
      return null;
    }

    public static Map<Tag,String> toTagMap(Map<String,String> values) {
      Map<Tag, String> tagMap = new EnumMap<Tag, String>(Tag.class);
      for (Tag tag : Tag.values()){
        if (values.containsKey(tag.getKey())){
          tagMap.put(tag, values.get(tag.getKey()));
        }
      }
      return tagMap;

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
      return (T) deserialize(getKey(),value,getType());
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

    public String getKey() { return name; }
    public boolean isMapped() { return true; }

    private String name;
    private Class<?> type;

    private Tag(String name, Class<?> type) {
      this.name = name;
      this.type = type;
    }
  }
  //================================================================================
  public enum MwgTag implements MetadataTag {
    LOCATION("Location",String.class),
    CITY("City",String.class),
    STATE("State",String.class),
    COUNTRY("Country",String.class),

    COPYRIGHT("Copyright",String.class),

    DATE_TIME_ORIGINAL("DateTimeOriginal",Date.class),
    CREATE_DATE("CreateDate",Date.class),
    MODIFY_DATE("ModifyDate",Date.class),

    CREATOR("Creator",String.class),
    DESCRIPTION("Description",String.class),
    KEYWORDS("Keywords",String[].class),

    ORIENTATION("Orientation",Integer.class),
    RATING("Rating",Integer.class),

    ;


    private String name;
    private Class<?> type;
    private MwgTag(String name, Class type) {
      this.name = name;
      this.type = type;
    }
    public String getKey() { return name; }
    public Class<?> getType() { return type; }
    public boolean isMapped() { return true; }
    public String toString() { return name; }
  }

  //================================================================================

  /**
   * A Custom Tag that the user defines.  Used to cover tags not in the enum.
   */
  public static class CustomTag implements MetadataTag {
    private final String name;
    private final Class type;
    private final boolean mapped;

    public CustomTag(String name, Class type) {
      this(name,type,!name.trim().endsWith(":all"));
    }
    public CustomTag(String name, Class type, boolean mapped) {
      this.name = name.trim();
      this.type = type;
      this.mapped = mapped;
    }

    public String getKey() {
      return name;
    }

    public Class getType() {
      return type;
    }

    public boolean isMapped() {
      return mapped;
    }

    public String toString() {
      return getKey();
    }
  }


  //================================================================================
  public enum TagGroup implements MetadataTag {
    EXIF("EXIF","exif:all"),
    IPTC("IPTC","iptc:all"),
    XMP("XMP","xmp:all"),
    ALL("ALL","all");

    private final String name;
    private final String key;

    private TagGroup(String name, String key) {
      this.name = name;
      this.key = key;
    }

    public String getName() {
      return name;
    }
    public String getKey() {
      return key;
    }

    public Class getType() {
      return Void.class;
    }

    public boolean isMapped() {
      return false;
    }

  }


  //================================================================================
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