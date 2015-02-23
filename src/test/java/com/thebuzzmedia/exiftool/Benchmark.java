package com.thebuzzmedia.exiftool;

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thebuzzmedia.exiftool.adapters.ExifToolService;

public class Benchmark {
	public static final int ITERATIONS = 25;
	private static Logger log = LoggerFactory.getLogger(Benchmark.class);

	public static void main(String[] args) throws IOException,
			InterruptedException {

		// System.setProperty(ExifToolNew3.ENV_EXIF_TOOL_PATH,
		// "D:\\Tools\\exiftool.exe");

		final Tag[] tags = Tag.values();
		final File[] images = new File("src/test/resources").listFiles();

		log.info("Benchmark [tags=" + tags.length + ", images=" + images.length
				+ ", iterations=" + ITERATIONS + "]");
		log.info("\t" + (images.length * ITERATIONS)
				+ " ExifToolNew3 process calls, "
				+ (tags.length * images.length * ITERATIONS)
				+ " total operations.\n");

		ExifToolService tool = RawExifTool.Factory.create();
		ExifToolService toolSO = RawExifTool.Factory.create(Feature.STAY_OPEN);

		/*
		 * -stay_open False
		 */
		log.info("\t[-stay_open False]");
		long elapsedTime = 0;

		for (int i = 1; i <= ITERATIONS; i++) {
			log.info(String.format("iteration %s of %s", i, ITERATIONS));
			elapsedTime += run(tool, images, tags);
		}

		log.info("\t\tElapsed Time: " + elapsedTime + " ms ("
				+ ((double) elapsedTime / 1000) + " secs)");
		/*
		 * -stay_open True
		 */
		log.info("\n\t[-stay_open True]");
		long elapsedTimeSO = 0;

		for (int i = 1; i <= ITERATIONS; i++) {
			log.info(String.format("iteration %s of %s", i, ITERATIONS));
			elapsedTimeSO += run(toolSO, images, tags);
		}

		log.info("\t\tElapsed Time: " + elapsedTimeSO + " ms ("
				+ ((double) elapsedTimeSO / 1000) + " secs - "
				+ ((float) elapsedTime / (float) elapsedTimeSO) + "x faster)");

		// Shut down the running exiftool proc.
		toolSO.close();
	}

	private static long run(ExifToolService tool, File[] images, Tag[] tags)
			throws IllegalArgumentException, SecurityException, IOException {
		long startTime = System.currentTimeMillis();

		for (File image : images) {
			tool.getImageMeta3(image, new ReadOptions(), tags);
		}

		return (System.currentTimeMillis() - startTime);
	}
}