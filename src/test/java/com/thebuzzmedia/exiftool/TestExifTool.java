package com.thebuzzmedia.exiftool;

import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * TestMetadata<p>
 *
 * @author Michael Rush (michaelrush@gmail.com)
 * @since Initially created 8/8/13
 */
public class TestExifTool {

  @Test
  public void testTags() throws Exception {
    ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
    Map<ExifTool.Tag,String> metadata;
    File imageFile;
    Set<ExifTool.Tag> keys;
    ExifTool.Tag tag;

    URL url = getClass().getResource("/kureckjones_jett_075_02-cropped.tif");
    imageFile = new File(url.toURI());

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

    url = getClass().getResource("/nexus-s-electric-cars.jpg");
    imageFile = new File(url.toURI());
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

  @Test
  public void testGroupTags() throws Exception {
    ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
    Map<String,String> metadata;
    URL url = getClass().getResource("/iptc_test-photoshop.jpg");
    File imageFile = new File(url.toURI());
    metadata = tool.getImageMeta(imageFile, ExifTool.Format.HUMAN_READABLE, ExifTool.TagGroup.IPTC);
    assertEquals(17, metadata.size());
    assertEquals("IPTC Content: Keywords", metadata.get("Keywords"));
    assertEquals("IPTC Status: Copyright Notice", metadata.get("CopyrightNotice"));
    assertEquals("IPTC Content: Description Writer", metadata.get("Writer-Editor"));
    //for (String key : metadata.keySet()){
    //  log.info(String.format("\t\t%s: %s", key, metadata.get(key)));
    //}
  }

  @Test
  public void testTag(){
    assertEquals("string value", "John Doe", ExifTool.Tag.AUTHOR.parseValue("John Doe"));
    assertEquals("integer value", 200, ExifTool.Tag.ISO.parseValue("200"));
    assertEquals("double value, from fraction", .25, ExifTool.Tag.SHUTTER_SPEED.parseValue("1/4"));
    assertEquals("double value, from decimal", .25, ExifTool.Tag.SHUTTER_SPEED.parseValue(".25"));
  }


    @Test
    public void testWriteTagStringNonDaemon() throws Exception{
        ExifTool tool = new ExifTool();
        URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
        Path imageFile = Paths.get(url.toURI());

        // Check the value is correct at the start
        Map<ExifTool.Tag,String> metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Now change it
        String newDate = "2014:01:23 10:07:05";
        Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
        newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);
        tool.addImageMetadata(imageFile.toFile(), newValues);

        // Finally check that it's updated
        metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("DateTimeOriginal tag is wrong", newDate, metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Finally copy the source file back over so the next test run is not affected by the change
        URL backup_url = getClass().getResource("/nexus-s-electric-cars.jpg_original");
        Path backupFile = Paths.get(backup_url.toURI());

        Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

    }

    @Test
    public void testWriteTagString() throws Exception{
        ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
        URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
        Path imageFile = Paths.get(url.toURI());

        // Check the value is correct at the start
        Map<ExifTool.Tag,String> metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Now change it
        String newDate = "2014:01:23 10:07:05";
        Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
        newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);
        tool.addImageMetadata(imageFile.toFile(), newValues);

        // Finally check that it's updated
        metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("DateTimeOriginal tag is wrong", newDate, metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Finally copy the source file back over so the next test run is not affected by the change
        URL backup_url = getClass().getResource("/nexus-s-electric-cars.jpg_original");
        Path backupFile = Paths.get(backup_url.toURI());

        Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    public void testWriteTagStringInvalidformat() throws Exception{
        ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
        URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
        Path imageFile = Paths.get(url.toURI());

        // Check the value is correct at the start
        Map<ExifTool.Tag,String> metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        String newDate = "2egek opkpgrpok";
        Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
        newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);

        // Now change it  to an invalid value which should fail
        tool.addImageMetadata(imageFile.toFile(), newValues);

        // Finally check that it's not updated
        metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("DateTimeOriginal tag is wrong", "2010:12:10 17:07:05", metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Finally copy the source file back over so the next test run is not affected by the change
        URL backup_url = getClass().getResource("/nexus-s-electric-cars.jpg_original");
        // might not exist
        if(backup_url != null) {
            Path backupFile = Paths.get(backup_url.toURI());
            Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    public void testWriteTagNumberNonDaemon() throws Exception{
        ExifTool tool = new ExifTool();
        URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
        Path imageFile = Paths.get(url.toURI());

        // Test what orientation value is at the start
        Map<ExifTool.Tag,String> metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION);
        assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)", metadata.get(ExifTool.Tag.ORIENTATION));

        // Now change it
        Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
        newValues.put(ExifTool.Tag.ORIENTATION, 3);

        tool.addImageMetadata(imageFile.toFile(), newValues);

        // Finally check the updated value
        metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION);
        assertEquals("Orientation tag updated value is wrong", "Rotate 180", metadata.get(ExifTool.Tag.ORIENTATION));

        // Finally copy the source file back over so the next test run is not affected by the change
        URL backup_url = getClass().getResource("/nexus-s-electric-cars.jpg_original");
        Path backupFile = Paths.get(backup_url.toURI());

        Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

    }

    @Test
    public void testWriteTagNumber() throws Exception{
        ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
        URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
        Path imageFile = Paths.get(url.toURI());

        // Test what orientation value is at the start
        Map<ExifTool.Tag,String> metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION);
        assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)", metadata.get(ExifTool.Tag.ORIENTATION));

        // Now change it
        Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
        newValues.put(ExifTool.Tag.ORIENTATION, 3);

        tool.addImageMetadata(imageFile.toFile(), newValues);

        // Finally check the updated value
        metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION);
        assertEquals("Orientation tag updated value is wrong", "Rotate 180", metadata.get(ExifTool.Tag.ORIENTATION));

        // Finally copy the source file back over so the next test run is not affected by the change
        URL backup_url = getClass().getResource("/nexus-s-electric-cars.jpg_original");
        Path backupFile = Paths.get(backup_url.toURI());

        Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

    }


    @Test
    public void testWriteMulipleTag() throws Exception{
        ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
        URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
        Path imageFile = Paths.get(url.toURI());

        // Test what orientation value is at the start
        Map<ExifTool.Tag,String> metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)", metadata.get(ExifTool.Tag.ORIENTATION));
        assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Now change them
        String newDate = "2014:01:23 10:07:05";
        Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
        newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);
        newValues.put(ExifTool.Tag.ORIENTATION, 3);

        tool.addImageMetadata(imageFile.toFile(), newValues);

        // Finally check the updated value
        metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("Orientation tag updated value is wrong", "Rotate 180", metadata.get(ExifTool.Tag.ORIENTATION));
        assertEquals("DateTimeOriginal tag is wrong", newDate, metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Finally copy the source file back over so the next test run is not affected by the change
        URL backup_url = getClass().getResource("/nexus-s-electric-cars.jpg_original");
        Path backupFile = Paths.get(backup_url.toURI());

        Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

    }

    @Test
    public void testWriteMulipleTagNonDaemon() throws Exception{
        ExifTool tool = new ExifTool();
        URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
        Path imageFile = Paths.get(url.toURI());

        // Test what orientation value is at the start
        Map<ExifTool.Tag,String> metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)", metadata.get(ExifTool.Tag.ORIENTATION));
        assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Now change them
        String newDate = "2014:01:23 10:07:05";
        Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
        newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);
        newValues.put(ExifTool.Tag.ORIENTATION, 3);

        tool.addImageMetadata(imageFile.toFile(), newValues);

        // Finally check the updated value
        metadata = tool.getImageMeta(imageFile.toFile(), ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION, ExifTool.Tag.DATE_TIME_ORIGINAL);
        assertEquals("Orientation tag updated value is wrong", "Rotate 180", metadata.get(ExifTool.Tag.ORIENTATION));
        assertEquals("DateTimeOriginal tag is wrong", newDate, metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

        // Finally copy the source file back over so the next test run is not affected by the change
        URL backup_url = getClass().getResource("/nexus-s-electric-cars.jpg_original");
        Path backupFile = Paths.get(backup_url.toURI());

        Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

    }
}
