package com.thebuzzmedia.exiftool;

import junit.framework.TestCase;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * TestMetadata<p>
 *
 * @author Michael Rush (michaelrush@gmail.com)
 * @since Initially created 8/8/13
 */
public class TestExifTool extends TestCase {

  private static final String TEST_FILES_PATH = "src/test/resources";
  private static Logger log = Logger.getLogger(TestExifTool.class);

  public void testTags() throws Exception {
    ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
    Map<ExifTool.Tag,String> metadata;
    File imageFile;
    Set<ExifTool.Tag> keys;
    ExifTool.Tag tag;

    imageFile = new File(TEST_FILES_PATH + "/kureckjones_jett_075_02-cropped.tif");
    metadata = tool.getImageMeta(imageFile, ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.values());
    assertEquals(22, metadata.size());

    keys = metadata.keySet();

    tag = ExifTool.Tag.IMAGE_WIDTH;
    assertTrue(keys.contains(tag));
    assertEquals(728, tag.parseValue(metadata.get(tag)));

    tag = ExifTool.Tag.IMAGE_HEIGHT;
    assertEquals(825, tag.parseValue(metadata.get(tag)));

    tag = ExifTool.Tag.MODEL;
    assertEquals("P 45+", tag.parseValue(metadata.get(tag)));


    imageFile = new File(TEST_FILES_PATH + "/nexus-s-electric-cars.jpg");
    metadata = tool.getImageMeta(imageFile, ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.values());
    assertEquals(23, metadata.size());

    keys = metadata.keySet();
    tag = ExifTool.Tag.IMAGE_WIDTH;
    assertTrue(keys.contains(tag));
    assertEquals(2560, tag.parseValue(metadata.get(tag)));

    tag = ExifTool.Tag.IMAGE_HEIGHT;
    assertEquals(1920, tag.parseValue(metadata.get(tag)));

    tag = ExifTool.Tag.MODEL;
    assertEquals("Nexus S", tag.parseValue(metadata.get(tag)));

    tag = ExifTool.Tag.ISO;
    assertEquals(50, tag.parseValue(metadata.get(tag)));

    tag = ExifTool.Tag.SHUTTER_SPEED;
    assertEquals("1/64", metadata.get(tag));
    assertEquals(0.015625, tag.parseValue(metadata.get(tag)));
  }

  public void testGroupTags() throws Exception {
    ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
    Map<String,String> metadata;
    File f = new File(TEST_FILES_PATH + "/iptc_test-photoshop.jpg");
    metadata = tool.getImageMeta(f, ExifTool.Format.HUMAN_READABLE, ExifTool.TagGroup.IPTC);
    assertEquals(17, metadata.size());
    assertEquals("IPTC Content: Keywords", metadata.get("Keywords"));
    assertEquals("IPTC Status: Copyright Notice", metadata.get("CopyrightNotice"));
    assertEquals("IPTC Content: Description Writer", metadata.get("Writer-Editor"));
    //for (String key : metadata.keySet()){
    //  log.info(String.format("\t\t%s: %s", key, metadata.get(key)));
    //}
  }

  public void testTag(){
    assertEquals("string value", "John Doe", ExifTool.Tag.AUTHOR.parseValue("John Doe"));
    assertEquals("integer value", 200, ExifTool.Tag.ISO.parseValue("200"));
    assertEquals("double value, from fraction", .25, ExifTool.Tag.SHUTTER_SPEED.parseValue("1/4"));
    assertEquals("double value, from decimal", .25, ExifTool.Tag.SHUTTER_SPEED.parseValue(".25"));
  }
}
