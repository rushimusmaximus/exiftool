package com.thebuzzmedia.exiftool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

// ================================================================================
/**
 * Represents an external exif process. Works for both single use and keep alive modes. This is the actual process, with
 * streams for reading and writing data.
 */
public final class ExifProcess {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExifProcess.class);

  private static class Pair<P1, P2> {
    final P1 _1;
    final P2 _2;

    public Pair(P1 _1, P2 _2) {
      this._1 = _1;
      this._2 = _2;
    }

    @Override
    public String toString() {
      return "Pair(" + _1 + "," + _2 + ")";
    }
  }

  private static final Map<String, Pair<String, ExifProcess>> all = Collections
      .synchronizedMap(new TreeMap<String, Pair<String, ExifProcess>>());
  static {
    LOG.debug("addShutdownHook");
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        if (!all.isEmpty()) {
          LOG.debug("Close all not closed processes:" + all.keySet());
          for (Entry<String, Pair<String, ExifProcess>> item : new HashSet<Entry<String, Pair<String, ExifProcess>>>(all.entrySet())) {
            LOG.debug("Close not closed process " + item, new RuntimeException());
            item.getValue()._2.close();
          }
        }
      }
    });
  }

  public static VersionNumber readVersion(String exifCmd) {
    ExifProcess process = new ExifProcess(false, Arrays.asList(exifCmd, "-ver"), Charset.defaultCharset());
    try {
      return new VersionNumber(process.readLine());
    } catch (IOException ex) {
      throw new RuntimeException(String.format("Unable to check version number of ExifToolNew3: %s", exifCmd));
    } finally {
      process.close();
    }
  }

  private static ExifProcess _execute(boolean keepAlive, List<String> args, Charset charset) {
    return new ExifProcess(keepAlive, args, charset);
  }

  public static List<String> executeToResults(String exifCmd, List<String> args, Charset charset) throws IOException {
    List<String> newArgs = new ArrayList<String>(args.size() + 1);
    newArgs.add(exifCmd);
    newArgs.addAll(args);
    ExifProcess process = _execute(false, newArgs, charset);
    try {
      return process.readResponse(args);
    } catch (Throwable e) {
      throw new RuntimeException(String.format("When executing %s we got %s", toCmd(newArgs), e.getMessage()), e);
    } finally {
      process.close();
    }
  }

  private static String toCmd(List<String> args) {
    StringBuilder sb = new StringBuilder();
    for (String arg : args) {
      sb.append(arg).append(" ");
    }
    return sb.toString();
  }

  //
  // public static String executeToString(String exifCmd, List<String> args, Charset charset) throws IOException {
  // return ExifProxy.$.toResponse(executeToResults(exifCmd,args,charset));
  // }
  //
  public static ExifProcess startup(String exifCmd, Charset charset) {
    List<String> args = Arrays.asList(exifCmd, "-stay_open", "True", "-@", "-");
    return _execute(true, args, charset);
  }

  private final ReentrantLock closeLock = new ReentrantLock(false);
  private final boolean keepAlive;
  private final Process process;
  private final BufferedReader reader;
  private final OutputStreamWriter writer;
  private final LineReaderThread errReader;
  private volatile boolean closed = false;

  public ExifProcess(boolean keepAlive, List<String> args, Charset charset) {
    this.keepAlive = keepAlive;
    LOG.debug(String.format("Attempting to start ExifToolNew3 process using args: %s", args));
    try {
      LOG.info("start background process: " + Joiner.on(" ").join(args));
      this.process = new ProcessBuilder(args).start();
      all.put(process.toString(), new Pair<String, ExifProcess>(toString(new RuntimeException("start of " + process)), this));
      this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      this.writer = new OutputStreamWriter(process.getOutputStream(), charset);
      this.errReader = new LineReaderThread("exif-process-err-reader", new BufferedReader(new InputStreamReader(process.getErrorStream())));
      this.errReader.setDaemon(true);
      errReader.start();
      LOG.debug("\tSuccessful " + process + " started.");
    } catch (Exception e) {
      String message = "Unable to start external ExifToolNew3 process using the execution arguments: " + args
          + ". Ensure ExifToolNew3 is installed correctly and runs using the command path '" + args.get(0)
          + "' as specified by the 'exiftool.path' system property.";

      LOG.debug(message);
      throw new RuntimeException(message, e);
    }
  }

  private String toString(Throwable throwable) {
    StringWriter sw = new StringWriter();
    throwable.printStackTrace(new PrintWriter(sw));
    return sw.getBuffer().toString();
  }

  public synchronized List<String> sendToRunning(List<String> args) throws IOException {
    return sendArgs(args);
  }

  public synchronized List<String> sendArgs(List<String> args) throws IOException {
    if (!keepAlive) {
      throw new IOException("Not KeepAlive Process");
    }
    StringBuilder builder = new StringBuilder();
    for (String arg : args) {
      builder.append(arg).append("\n");
    }
    builder.append("-execute\n");
    LOG.info("exiftool " + Joiner.on(" ").join(args));
    writeFlush(builder.toString());
    return readResponse(args);
  }

  public synchronized void writeFlush(String message) throws IOException {
    if (closed)
      throw new IOException(ExifToolNew3.STREAM_CLOSED_MESSAGE);
    writer.write(message);
    writer.flush();
  }

  public synchronized String readLine() throws IOException {
    if (closed)
      throw new IOException(ExifToolNew3.STREAM_CLOSED_MESSAGE);
    return reader.readLine();
  }

  public synchronized List<String> readResponse(List<String> args) throws IOException {
    if (closed)
      throw new IOException(ExifToolNew3.STREAM_CLOSED_MESSAGE);
    LOG.debug("Reading response back from ExifToolNew3...");
    String line;
    List<String> all = new ArrayList<String>();

    while ((line = reader.readLine()) != null) {
      if (closed) {
        LOG.info("stream closed message");
        throw new IOException(ExifToolNew3.STREAM_CLOSED_MESSAGE);
      }
      LOG.debug("stream line read [" + line + "]");
      /*
       * When using a persistent ExifToolNew3 process, it terminates its output to us with a "{ready}" clause on a new
       * line, we need to look for it and break from this loop when we see it otherwise this process will hang
       * indefinitely blocking on the input stream with no data to read.
       */
      if (keepAlive && line.equals("{ready}")) {
        break;
      }
      all.add(line);
    }
    Preconditions.checkNotNull(line, "Should wait to get {ready}");
    String error = readError();
    if (error != null) {
      //
      // }
      // if (all.isEmpty()) {
      // //since no result came it is a chance we have an error
      // String error = null;
      // try {
      // while ((error = readError()) == null) {
      // Thread.currentThread().sleep(100);
      // }
      // } catch (InterruptedException e) {
      // throw new RuntimeException("Didn't get anything back from exiftool with args [" + args + "].", e);
      // }
      String message = error + ". " + all.size() + " lines where read [" + all + "] for exiftool with args [" + args + "].";
      // if(result.contains("No matching files")){
      throw new ExifError(message);
      // }else{
      // LOG.info(message);
      // }
    }
    return all;
  }

  private String readError() throws ExifError {
    if (errReader.hasLines()) {
      StringBuffer sb = new StringBuffer();
      for (String error : errReader.takeLines()) {
        if (error.toLowerCase().startsWith("error")) {
          throw new ExifError(error);
        }
        sb.append(error);
      }
      String result = sb.toString();
      return result;
    } else {
      return null;
    }
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
          try {
            LOG.debug("Closing Read stream...");
            reader.close();
            LOG.debug("\tSuccessful");
          } catch (Exception e) {
            // no-op, just try to close it.
            LOG.debug("", e);
          }

          try {
            LOG.debug("Attempting to close ExifToolNew3 daemon process, issuing '-stay_open\\nFalse\\n' command...");
            writer.write("-stay_open\nFalse\n");
            writer.flush();
          } catch (IOException e) {
            // log.error(ex,ex);
            LOG.debug("", e);
          }

          try {
            LOG.debug("Closing Write stream...");
            writer.close();
            LOG.debug("\tSuccessful");
          } catch (Exception e) {
            // no-op, just try to close it.
            LOG.debug("", e);
          }

          try {
            LOG.debug("Closing Error stream...");
            errReader.close();
            LOG.debug("\tSuccessful");
          } catch (Exception e) {
            // no-op, just try to close it.
            LOG.debug("", e);
          }
          LOG.debug("Read/Write streams successfully closed.");

          try {
            LOG.debug("\tDestroy process " + process + "...");
            process.destroy();
            all.remove(process.toString());
            LOG.debug("\tDestroy process " + process + " done => " + all.keySet());
          } catch (Exception e) {
            //
            LOG.debug("", e);
          }
          // process = null;

        }
      } finally {
        closeLock.unlock();
      }
    }
  }

  @Override
  protected void finalize() throws Throwable {
    LOG.debug("\tFinalize process " + process + ".");
    close();
    super.finalize();
  }
}