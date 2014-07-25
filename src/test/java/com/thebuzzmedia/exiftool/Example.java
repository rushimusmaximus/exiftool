package com.thebuzzmedia.exiftool;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thebuzzmedia.exiftool.ExifTool.Feature;
import com.thebuzzmedia.exiftool.ExifTool.Format;

public class Example {

  private static Logger log = LoggerFactory.getLogger(Example.class);
  private static final String TEST_FILES_PATH = "src/test/resources";

	public static void main(String[] args) throws IOException, InterruptedException {

    //System.setProperty(ExifTool.ENV_EXIF_TOOL_PATH, "D:\\Tools\\exiftool.exe");
    ExifTool tool = new ExifTool(Feature.STAY_OPEN);

    File[] images = new File(TEST_FILES_PATH).listFiles();

    //list all first-class tags
    for (File f : images) {
      log.info("\n[" + f.getName() + "]");
      Map<ExifTool.Tag,String> metadata = tool.getImageMeta(f, Format.HUMAN_READABLE, ExifTool.Tag.values());
      for (ExifTool.Tag key : metadata.keySet()){
        log.info(String.format("\t\t%s: %s", key.getName(), metadata.get(key)));
      }
    }

    log.info("\n\n** GET TAGS BY GROUP");
    //list all XMP, IPTC and XMP tags
    File f = new File(TEST_FILES_PATH + "/kureckjones_jett_075_02-cropped.tif");
    for (ExifTool.TagGroup tagGroup : new ExifTool.TagGroup[] {ExifTool.TagGroup.EXIF, ExifTool.TagGroup.IPTC, ExifTool.TagGroup.XMP}){
      Map<String,String> metadata = tool.getImageMeta(f, Format.HUMAN_READABLE, tagGroup);
      log.info(tagGroup.getName());
      for (String key : metadata.keySet()){
        log.info(String.format("\t\t%s: %s", key, metadata.get(key)));
      }
    }

    tool.close();
  }
}