package com.thebuzzmedia.exiftool;

import java.io.File;
import java.io.IOException;

/**
 * Class used to handle XMP Sidecar files using exiftool.
 * 
 * @author Clinton LaForest (clafore@bgsu.edu)
 * @since 1.2_thespiritx
 */
public class Sidecar {
	/**
	 * 
	 */
	private final ExifToolNew2 exifTool;

	public Sidecar(ExifToolNew2 exifTool, Feature feature) {
		this.exifTool = exifTool;

	}

	/**
	 * Used to merge a XMP Sidecar file with an image.
	 * 
	 * @param xmp
	 *            The xmp sidecar file.
	 * 
	 * @param file
	 *            The image file.
	 * 
	 * @param preserve
	 *            <code>true</code> - preserves name mappings <code>false</code>
	 *            - uses preferred name mappings
	 * 
	 * @return <code>void</code>
	 * 
	 */
	public void merge(File xmp, File file, Boolean preserve) {
		if (xmp == null)
			throw new IllegalArgumentException(
					"xmp cannot be null and must be a valid xmp sidecar stream.");
		if (file == null)
			throw new IllegalArgumentException("file cannot be null");
		if (preserve == null)
			preserve = false;
		if (!file.canWrite())
			throw new SecurityException(
					"Unable to read the given image ["
							+ file.getAbsolutePath()
							+ "], ensure that the image exists at the given path and that the executing Java process has permissions to read it.");

		long startTime = System.currentTimeMillis();

		ExifToolNew3.log("Writing %s tags to image: %s", xmp.getAbsolutePath(),
				file.getAbsolutePath());

		long exifToolCallElapsedTime = 0;

		/*
		 * Using ExifToolNew3 in daemon mode (-stay_open True) executes different
		 * code paths below. So establish the flag for this once and it is
		 * reused a multitude of times later in this method to figure out where
		 * to branch to.
		 */
		boolean stayOpen = this.exifTool.featureSet.contains(Feature.STAY_OPEN);

		this.exifTool.args.clear();

		if (stayOpen) {
			ExifToolNew3.log("\tUsing ExifToolNew3 in daemon mode (-stay_open True)...");

			// Always reset the cleanup task.
			this.exifTool.resetCleanupTask();

			/*
			 * If this is our first time calling getImageMeta with a stayOpen
			 * connection, set up the persistent process and run it so it is
			 * ready to receive commands from us.
			 */
			if (this.exifTool.streams == null) {
				ExifToolNew3.log("\tStarting daemon ExifToolNew3 process and creating read/write streams (this only happens once)...");

				this.exifTool.args.add(exifTool.EXIF_TOOL_PATH);
				this.exifTool.args.add("-stay_open");
				this.exifTool.args.add("True");
				this.exifTool.args.add("-@");
				this.exifTool.args.add("-");

				// Begin the persistent ExifToolNew3 process.
				this.exifTool.streams = exifTool
						.startExifToolProcess(this.exifTool.args);
			}

			ExifToolNew3.log("\tStreaming arguments to ExifToolNew3 process...");

			try {
				this.exifTool.streams.writer.write("-tagsfromfile\n");

				this.exifTool.streams.writer.write(file.getAbsolutePath());
				this.exifTool.streams.writer.write("\n");

				if (preserve) {
					this.exifTool.streams.writer.write("-all:all");
					this.exifTool.streams.writer.write("\n");
				} else {
					this.exifTool.streams.writer.write("-xmp");
					this.exifTool.streams.writer.write("\n");
				}

				this.exifTool.streams.writer.write(xmp.getAbsolutePath());
				this.exifTool.streams.writer.write("\n");

				ExifToolNew3.log("\tExecuting ExifToolNew3...");

				// Begin tracking the duration ExifToolNew3 takes to respond.
				exifToolCallElapsedTime = System.currentTimeMillis();

				// Run ExifToolNew3 on our file with all the given arguments.
				this.exifTool.streams.writer.write("-execute\n");
				this.exifTool.streams.writer.flush();

			} catch (IOException e) {
				ExifToolNew3.log("\tError received in stayopen stream: %s",
						e.getMessage());
			} // compact output
		} else {
			ExifToolNew3.log("\tUsing ExifToolNew3 in non-daemon mode (-stay_open False)...");

			/*
			 * Since we are not using a stayOpen process, we need to setup the
			 * execution arguments completely each time.
			 */
			this.exifTool.args.add(exifTool.EXIF_TOOL_PATH);

			this.exifTool.args.add("-tagsfromfile"); // compact output

			this.exifTool.args.add(file.getAbsolutePath());

			if (preserve) {
				this.exifTool.args.add("-all:all");
			} else {
				this.exifTool.args.add("-xmp");
			}

			this.exifTool.args.add(xmp.getAbsolutePath());

			// Run the ExifToolNew3 with our args.
			this.exifTool.streams = exifTool
					.startExifToolProcess(this.exifTool.args);

			// Begin tracking the duration ExifToolNew3 takes to respond.
			exifToolCallElapsedTime = System.currentTimeMillis();
		}

		ExifToolNew3.log("\tReading response back from ExifToolNew3...");

		String line = null;

		try {
			while ((line = this.exifTool.streams.reader.readLine()) != null) {
				/*
				 * When using a persistent ExifToolNew3 process, it terminates its
				 * output to us with a "{ready}" clause on a new line, we need
				 * to look for it and break from this loop when we see it
				 * otherwise this process will hang indefinitely blocking on the
				 * input stream with no data to read.
				 */
				if (stayOpen && line.equals("{ready}"))
					break;
			}
		} catch (IOException e) {
			ExifToolNew3.log("\tError received in response: %d", e.getMessage());
		}

		// Print out how long the call to external ExifToolNew3 process took.
		ExifToolNew3.log("\tFinished reading ExifToolNew3 response in %d ms.",
				(System.currentTimeMillis() - exifToolCallElapsedTime));

		/*
		 * If we are not using a persistent ExifToolNew3 process, then after running
		 * the command above, the process exited in which case we need to clean
		 * our streams up since it no longer exists. If we were using a
		 * persistent ExifToolNew3 process, leave the streams open for future calls.
		 */
		if (!stayOpen)
			this.exifTool.streams.close();

		ExifToolNew3.log("\tImage Meta Processed in %d ms [write %s tags]",
				(System.currentTimeMillis() - startTime), xmp.getAbsolutePath());

	}

	/**
	 * Used to export a XMP Sidecar file from an image.
	 * 
	 * @param xmp
	 *            The xmp sidecar file.
	 * 
	 * @param file
	 *            The image file.
	 * 
	 * @param preserve
	 *            <code>true</code> - preserves name mappings <code>false</code>
	 *            - uses preferred name mappings
	 * 
	 * @return <code>void</code>
	 * 
	 */
	public void export(File xmp, File file, boolean preserve) {

	}
}