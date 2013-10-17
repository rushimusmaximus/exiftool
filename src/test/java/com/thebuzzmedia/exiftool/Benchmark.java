package com.thebuzzmedia.exiftool;

import com.thebuzzmedia.exiftool.ExifTool.Feature;
import com.thebuzzmedia.exiftool.ExifTool.Tag;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class Benchmark {

	public static final int ITERATIONS = 25;
  private static Logger log = Logger.getLogger(Benchmark.class);


	public static void main(String[] args) throws IOException, InterruptedException {

    //System.setProperty(ExifTool.ENV_EXIF_TOOL_PATH, "D:\\Tools\\exiftool.exe");

    final Tag[] tags = Tag.values();
    final File[] images = new File("src/test/resources").listFiles();

		log.info("Benchmark [tags=" + tags.length + ", images="
            + images.length + ", iterations=" + ITERATIONS + "]");
    log.info("\t" + (images.length * ITERATIONS)
				+ " ExifTool process calls, "
				+ (tags.length * images.length * ITERATIONS)
				+ " total operations.\n");

		ExifTool tool = new ExifTool();
		ExifTool toolSO = new ExifTool(Feature.STAY_OPEN);

		/*
		 * -stay_open False
		 */
		log.info("\t[-stay_open False]");
		long elapsedTime = 0;

		for (int i = 1; i <= ITERATIONS; i++){
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

	private static long run(ExifTool tool, File[] images, Tag[] tags)
			throws IllegalArgumentException, SecurityException, IOException {
		long startTime = System.currentTimeMillis();

    for (File image : images){
      tool.getImageMeta(image, tags);
    }

		return (System.currentTimeMillis() - startTime);
	}
}