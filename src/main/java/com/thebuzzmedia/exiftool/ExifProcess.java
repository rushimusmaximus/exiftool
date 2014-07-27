package com.thebuzzmedia.exiftool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

// ================================================================================
/**
 * Represents an external exif process. Works for both single use and keep alive
 * modes. This is the actual process, with streams for reading and writing data.
 */
public final class ExifProcess {
	private static class Pair<P1,P2>{
		final P1 _1;
		final P2 _2;
		public Pair(P1 _1, P2 _2){
			this._1 = _1;
			this._2 = _2;
		}
		@Override
		public String toString() {
			return "Pair("+_1+","+_2+")";
		}
	}
	private static final Map<String,Pair<String,ExifProcess>> all = Collections.synchronizedMap(new TreeMap<String,Pair<String,ExifProcess>>());
	static{
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				ExifTool.log.info("Close all remaining processes:" + all.keySet());
				for(Entry<String, Pair<String, ExifProcess>> item : all.entrySet()){
					ExifTool.log.info("Close leaked process " + item);
					item.getValue()._2.close();
				}
			}
		});
	}
	/**
	 * Compiled {@link Pattern} of ": " used to split compact output from
	 * ExifTool evenly into name/value pairs.
	 */
	private static final Pattern TAG_VALUE_PATTERN = Pattern
			.compile("\\s*:\\s*");

	public static VersionNumber readVersion(String exifCmd) {
		ExifProcess process = new ExifProcess(false, Arrays.asList(exifCmd,
				"-ver"));
		try {
			return new VersionNumber(process.readLine());
		} catch (IOException ex) {
			throw new RuntimeException(String.format(
					"Unable to check version number of ExifTool: %s", exifCmd));
		} finally {
			process.close();
		}
	}

	public static ExifProcess _execute(boolean keepAlive, List<String> args) {
		return new ExifProcess(keepAlive, args);
	}

	public static Map<String, String> executeToResults(String exifCmd,
			List<String> args) throws IOException {
		List<String> newArgs = new ArrayList<String>(args.size() + 1);
		newArgs.add(exifCmd);
		newArgs.addAll(args);
		ExifProcess process = _execute(false, newArgs);
		try {
			return process.readResponse();
		}catch(Throwable e){
			throw new RuntimeException(String.format("When executing %s we got %s",toCmd(newArgs),e.getMessage()),e);
		} finally {
			process.close();
		}
	}
	private static String toCmd(List<String> args){
		StringBuilder sb = new StringBuilder();
		for(String arg:args){
			sb.append(arg).append(" ");
		}
		return sb.toString();
	}

	public static String executeToString(String exifCmd, List<String> args)
			throws IOException {
		List<String> newArgs = new ArrayList<String>(args.size() + 1);
		newArgs.add(exifCmd);
		newArgs.addAll(args);
		ExifProcess process = _execute(false, newArgs);
		try {
			return process.readResponseString();
		} finally {
			process.close();
		}
	}

	public static ExifProcess startup(String exifCmd) {
		List<String> args = Arrays.asList(exifCmd, "-stay_open", "True", "-@",
				"-");
		return _execute(true, args);
	}

	private final ReentrantLock closeLock = new ReentrantLock(false);
	private final boolean keepAlive;
	private final Process process;
	private final BufferedReader reader;
	private final OutputStreamWriter writer;
	private final LineReaderThread errReader;
	private volatile boolean closed = false;

	public ExifProcess(boolean keepAlive, List<String> args) {
		this.keepAlive = keepAlive;
		ExifTool.log.debug(String.format(
				"Attempting to start ExifTool process using args: %s", args));
		try {
			this.process = new ProcessBuilder(args).start();
			all.put(process.toString(), new Pair<String, ExifProcess>(toString(new RuntimeException("start of "+process)),this));
			this.reader = new BufferedReader(new InputStreamReader(
					process.getInputStream()));
			this.writer = new OutputStreamWriter(process.getOutputStream());
			this.errReader = new LineReaderThread("exif-process-err-reader",
					new BufferedReader(new InputStreamReader(
							process.getErrorStream())));
			errReader.start();
			ExifTool.log.debug("\tSuccessful " + process + " started.");
		} catch (Exception e) {
			String message = "Unable to start external ExifTool process using the execution arguments: "
					+ args
					+ ". Ensure ExifTool is installed correctly and runs using the command path '"
					+ args.get(0)
					+ "' as specified by the 'exiftool.path' system property.";

			ExifTool.log.debug(message);
			throw new RuntimeException(message, e);
		}
	}

	private String toString(Throwable throwable) {
		StringWriter sw = new StringWriter();
		throwable.printStackTrace(new PrintWriter(sw));
		return sw.getBuffer().toString();
	}

	public synchronized Map<String, String> sendToRunning(List<String> args)
			throws IOException {
		return sendArgs(args);
	}

	public synchronized Map<String, String> sendArgs(List<String> args)
			throws IOException {
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
		if (closed)
			throw new IOException(ExifTool.STREAM_CLOSED_MESSAGE);
		writer.write(message);
		writer.flush();
	}

	public synchronized String readLine() throws IOException {
		if (closed)
			throw new IOException(ExifTool.STREAM_CLOSED_MESSAGE);
		return reader.readLine();
	}

	public synchronized Map<String, String> readResponse() throws IOException {
		if (closed)
			throw new IOException(ExifTool.STREAM_CLOSED_MESSAGE);
		ExifTool.log.debug("Reading response back from ExifTool...");
		Map<String, String> resultMap = new HashMap<String, String>(500);
		String line;

		while ((line = reader.readLine()) != null) {
			if (closed)
				throw new IOException(ExifTool.STREAM_CLOSED_MESSAGE);
			String[] pair = TAG_VALUE_PATTERN.split(line, 2);
			if (pair.length == 2) {
				resultMap.put(pair[0], pair[1]);
				ExifTool.log.debug(String.format(
						"\tRead Tag [name=%s, value=%s]", pair[0], pair[1]));
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
		if (errReader.hasLines()) {
			for (String error : errReader.takeLines()) {
				if (error.toLowerCase().startsWith("error")) {
					throw new ExifError(error);
				}
			}
		}
		return resultMap;
	}

	public synchronized String readResponseString() throws IOException {
		if (closed)
			throw new IOException(ExifTool.STREAM_CLOSED_MESSAGE);
		ExifTool.log.debug("Reading response back from ExifTool...");
		String line;
		StringBuilder result = new StringBuilder();
		while ((line = reader.readLine()) != null) {
			if (closed)
				throw new IOException(ExifTool.STREAM_CLOSED_MESSAGE);

			/*
			 * When using a persistent ExifTool process, it terminates its
			 * output to us with a "{ready}" clause on a new line, we need to
			 * look for it and break from this loop when we see it otherwise
			 * this process will hang indefinitely blocking on the input stream
			 * with no data to read.
			 */
			if (keepAlive && line.equals("{ready}")) {
				break;
			} else
				result.append(line);
		}
		return result.toString();
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
						ExifTool.log.debug("Closing Read stream...");
						reader.close();
						ExifTool.log.debug("\tSuccessful");
					} catch (Exception e) {
						// no-op, just try to close it.
					}

					try {
						ExifTool.log
								.debug("Attempting to close ExifTool daemon process, issuing '-stay_open\\nFalse\\n' command...");
						writer.write("-stay_open\nFalse\n");
						writer.flush();
					} catch (IOException ex) {
						// log.error(ex,ex);
					}

					try {
						ExifTool.log.debug("Closing Write stream...");
						writer.close();
						ExifTool.log.debug("\tSuccessful");
					} catch (Exception e) {
						// no-op, just try to close it.
					}

					try {
						ExifTool.log.debug("Closing Error stream...");
						errReader.close();
						ExifTool.log.debug("\tSuccessful");
					} catch (Exception e) {
						// no-op, just try to close it.
					}

					ExifTool.log
							.debug("Read/Write streams successfully closed.");

					try {
						ExifTool.log.debug("\tDestroy process " + process + "...");
						process.destroy();
						all.remove(process.toString());
						ExifTool.log.debug("\tDestroy process " + process + " done => "+all.keySet());
					} catch (Exception e) {
						//
						ExifTool.log.debug("", e);
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
		ExifTool.log.debug("\tFinalize process " + process + ".");
		close();
		super.finalize();
	}
}