package com.thebuzzmedia.exiftool;

import java.io.BufferedReader;
import java.io.OutputStreamWriter;

/**
 * Simple class used to house the read/write streams used to communicate with an
 * external ExifToolNew3 process as well as the logic used to safely close the
 * streams when no longer needed.
 * <p/>
 * This class is just a convenient way to group and manage the read/write
 * streams as opposed to making them dangling member variables off of ExifToolNew3
 * directly.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
class IOStream {
	BufferedReader reader;
	OutputStreamWriter writer;

	public IOStream(BufferedReader reader, OutputStreamWriter writer) {
		this.reader = reader;
		this.writer = writer;
	}

	public void close() {
		try {
			ExifToolNew3.log("\tClosing Read stream...");
			reader.close();
			ExifToolNew3.log("\t\tSuccessful");
		} catch (Exception e) {
			// no-op, just try to close it.
		}

		try {
			ExifToolNew3.log("\tClosing Write stream...");
			writer.close();
			ExifToolNew3.log("\t\tSuccessful");
		} catch (Exception e) {
			// no-op, just try to close it.
		}

		// Null the stream references.
		reader = null;
		writer = null;

		ExifToolNew3.log("\tRead/Write streams successfully closed.");
	}
}