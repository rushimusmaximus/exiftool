package com.thebuzzmedia.exiftool;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thebuzzmedia.exiftool.adapters.ExifToolService;

public class Example {

	private static Logger log = LoggerFactory.getLogger(Example.class);
	private static final String TEST_FILES_PATH = "src/test/resources";

	public static void main(String[] args) throws IOException,
			InterruptedException {

		// System.setProperty(ExifToolNew3.ENV_EXIF_TOOL_PATH,
		// "D:\\Tools\\exiftool.exe");
		ExifToolService tool = RawExifTool.Factory.create(Feature.STAY_OPEN);

		File[] images = new File(TEST_FILES_PATH).listFiles();

		// list all first-class tags
		for (File f : images) {
			log.info("\n[{}]", f.getName());
			Map<MetadataTag, String> metadata = tool.getImageMeta4(f,
					new ReadOptions(), Format.HUMAN_READABLE, Tag.values());
			for (MetadataTag key : metadata.keySet()) {
				log.info(String.format("\t\t%s: %s", key.getKey(),
						metadata.get(key)));
			}
		}

		log.info("\n\n** GET TAGS BY GROUP");
		// list all XMP, IPTC and XMP tags
		File f = new File(TEST_FILES_PATH
				+ "/kureckjones_jett_075_02-cropped.tif");
		for (TagGroup tagGroup : new TagGroup[] {
				TagGroup.EXIF, TagGroup.IPTC,
				TagGroup.XMP }) {
			Map<String, String> metadata = tool.getImageMeta5(f,
					new ReadOptions(), Format.HUMAN_READABLE, tagGroup);
			log.info(tagGroup.getKey());
			for (String key : metadata.keySet()) {
				log.info(String.format("\t\t%s: %s", key, metadata.get(key)));
			}
		}

		tool.close();
	}
}