package com.thebuzzmedia.exiftool;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A Thread which reads lines from a BufferedReader and puts them in a queue, so
 * they can be read from with out blocking. This is used when reading from a
 * process.err input.
 * 
 * Remember to start thread!!
 * 
 * @author msgile
 * @author $LastChangedBy$
 * @version $Revision$ $LastChangedDate$
 * @since 7/25/14
 */
public class LineReaderThread extends Thread {
	private BufferedReader reader;
	private BlockingQueue<String> lineBuffer = new ArrayBlockingQueue<String>(
			50, true);

	public LineReaderThread(String name, BufferedReader reader) {
		super(name);
		this.reader = reader;
	}

	public LineReaderThread(ThreadGroup group, String name,
			BufferedReader reader) {
		super(group, name);
		this.reader = reader;
	}

	@Override
	public void run() {
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (!line.isEmpty()) {
					lineBuffer.put(line);
				}
			}
		} catch (IOException ex) {
			ExifToolNew3.log.warn("Error in LineReaderThread.",ex);
		} catch (InterruptedException ignored) {
			ExifToolNew3.log.debug("er:",ignored);
		}
	}

	public boolean isEmpty() {
		return lineBuffer.isEmpty();
	}

	public boolean hasLines() {
		return !lineBuffer.isEmpty();
	}

	/**
	 * Takes all lines from the buffer or returns empty list.
	 */
	public List<String> takeLines() {
		List<String> lines = new ArrayList<String>(lineBuffer.size());
		lineBuffer.drainTo(lines);
		return lines;
	}

	/**
	 * Reads line without blocking, will return Null if no lines in buffer.
	 */
	public String readLine() {
		return lineBuffer.poll();
	}

	public void close() {
		interrupt();
		try {
			reader.close();
		} catch (IOException ignored) {
			;
		}
	}

}
