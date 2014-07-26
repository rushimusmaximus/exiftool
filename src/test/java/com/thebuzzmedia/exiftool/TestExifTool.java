package com.thebuzzmedia.exiftool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TestMetadata
 * <p>
 * 
 * @author Michael Rush (michaelrush@gmail.com)
 * @since Initially created 8/8/13
 */

public class TestExifTool {

	private static final String TEST_FILES_PATH = "src/test/resources";
	private static Logger log = LoggerFactory.getLogger(TestExifTool.class);

	@Test
	public void testSingleTool() throws Exception {
		ExifTool tool = new ExifTool();
		try {
			assertTrue(runTests(tool, ""));
		} finally {
			tool.shutdown();
		}

		tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		try {
			assertTrue(runTests(tool, ""));
		} finally {
			tool.shutdown();
		}

		tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		try {
			tool.startup();
			assertTrue(runTests(tool, ""));
		} finally {
			tool.shutdown();
		}
	}

	@Test
	public void testConcurrent() throws Exception {

		int toolCount = 5;

		List<Thread> threads = new ArrayList<Thread>(toolCount);
		for (int i = 1; i <= toolCount; i++) {
			String toolName = "tool" + i;
			Thread t = new Thread(toolName) {
				@Override
				public void run() {
					log.info(getName() + ": starting");
					ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
					try {
						runTests(tool, getName());
						log.info(getName() + ": finished");
					} catch (IOException e) {
						log.error(e.getMessage(), e);
						fail(e.getMessage());
					} catch (URISyntaxException e) {
						log.error(e.getMessage(), e);
						fail(e.getMessage());
					} finally {
						tool.shutdown();
					}
					log.info(getName() + ": finished");
				}
			};
			t.start();
			threads.add(t);
		}

		for (Thread t : threads) {
			t.run();
		}
	}

	@Test
	public void testManyThreadsOneTool() throws Exception {
		final ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		try {
			Thread[] threads = new Thread[20];
			for (int i = 0; i < threads.length; i++) {
				final String label = "run " + i;
				threads[i] = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							for (int j = 0; j < 5; j++) {
								runTests(tool, label);
							}
							log.info("DONE: " + label + " success!");
						} catch (IOException ex) {
							fail(label);
						} catch (URISyntaxException e) {
							log.error(e.getMessage(), e);
							fail(e.getMessage());
						}
					}
				}, label);
			}
			for (Thread thread : threads) {
				thread.start();
			}
			for (Thread thread : threads) {
				thread.join();
			}
		} finally {
			tool.shutdown();
		}
	}

	@Test
	public void testProcessTimeout() throws Exception {
		final ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		try {
			tool.setRunTimeout(1);
			runTests(tool, "will fail");
			fail("should have failed");
		} catch (IOException ex) {
			;
		} finally {
			tool.shutdown();
		}
	}

	public boolean runTests(ExifTool tool, String runId) throws IOException,
			URISyntaxException {

		Map<ExifTool.Tag, String> metadata;
		File imageFile;
		Set<ExifTool.Tag> keys;
		ExifTool.Tag tag;

		URL url = getClass()
				.getResource("/kureckjones_jett_075_02-cropped.tif");
		imageFile = new File(url.toURI());
		metadata = tool.getImageMeta(imageFile, ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.values());
		assertEquals(25, metadata.size());

		keys = metadata.keySet();

		tag = ExifTool.Tag.IMAGE_WIDTH;
		assertTrue(keys.contains(tag));
		assertEquals(728, tag.parseValue(metadata.get(tag)));

		tag = ExifTool.Tag.IMAGE_HEIGHT;
		assertEquals(825, tag.parseValue(metadata.get(tag)));

		tag = ExifTool.Tag.MODEL;
		assertEquals("P 45+", tag.parseValue(metadata.get(tag)));
		log.info(runId + ": finished image 1");

		url = getClass().getResource("/nexus-s-electric-cars.jpg");
		imageFile = new File(url.toURI());
		metadata = tool.getImageMeta(imageFile, ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.values());
		assertEquals(24, metadata.size());

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
		log.info(runId + ": finished image 2");
		return true;
	}

	@Test
	public void testGroupTags() throws Exception {
		ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		try {
			Map<String, String> metadata;

			URL url = getClass().getResource("/iptc_test-photoshop.jpg");
			File f = new File(url.toURI());
			metadata = tool.getImageMeta(f, ExifTool.Format.HUMAN_READABLE,
					ExifTool.TagGroup.IPTC);
			assertEquals(17, metadata.size());
			assertEquals("IPTC Content: Keywords", metadata.get("Keywords"));
			assertEquals("IPTC Status: Copyright Notice",
					metadata.get("CopyrightNotice"));
			assertEquals("IPTC Content: Description Writer",
					metadata.get("Writer-Editor"));
			// for (String key : metadata.keySet()){
			// log.info(String.format("\t\t%s: %s", key, metadata.get(key)));
			// }
		} finally {
			tool.shutdown();
		}
	}

	@Test
	public void testTag() {
		assertEquals("string value", "John Doe",
				ExifTool.Tag.AUTHOR.parseValue("John Doe"));
		assertEquals("integer value", 200, ExifTool.Tag.ISO.parseValue("200"));
		assertEquals("double value, from fraction", .25,
				ExifTool.Tag.SHUTTER_SPEED.parseValue("1/4"));
		assertEquals("double value, from decimal", .25,
				ExifTool.Tag.SHUTTER_SPEED.parseValue(".25"));
	}

	@Test
	public void testVersionNumber() {
		assertTrue(new ExifTool.VersionNumber("1.2")
				.isBeforeOrEqualTo(new ExifTool.VersionNumber("1.2.3")));
		assertTrue(new ExifTool.VersionNumber(1, 2)
				.isBeforeOrEqualTo(new ExifTool.VersionNumber("1.2")));
		assertTrue(new ExifTool.VersionNumber(1, 2, 3)
				.isBeforeOrEqualTo(new ExifTool.VersionNumber("1.3")));
		assertTrue(new ExifTool.VersionNumber(1, 2, 3)
				.isBeforeOrEqualTo(new ExifTool.VersionNumber(2, 1)));
	}

	@Test
	public void testWriteTagStringNonDaemon() throws Exception {
		ExifTool tool = new ExifTool();
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get(url.toURI());

		// Check the value is correct at the start
		Map<ExifTool.Tag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("Wrong starting value", "2010:12:10 17:07:05",
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Now change it
		String newDate = "2014:01:23 10:07:05";
		Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
		newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);
		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check that it's updated
		metadata = tool
				.getImageMeta(imageFile.toFile(),
						ExifTool.Format.HUMAN_READABLE,
						ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("DateTimeOriginal tag is wrong", newDate,
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
		URL backup_url = getClass().getResource(
				"/nexus-s-electric-cars.jpg_original");
		Path backupFile = Paths.get(backup_url.toURI());

		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

	}

	@Test
	public void testWriteTagString() throws Exception {
		ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get(url.toURI());

		// Check the value is correct at the start
		Map<ExifTool.Tag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("Wrong starting value", "2010:12:10 17:07:05",
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Now change it
		String newDate = "2014:01:23 10:07:05";
		Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
		newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);
		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check that it's updated
		metadata = tool
				.getImageMeta(imageFile.toFile(),
						ExifTool.Format.HUMAN_READABLE,
						ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("DateTimeOriginal tag is wrong", newDate,
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
		URL backup_url = getClass().getResource(
				"/nexus-s-electric-cars.jpg_original");
		Path backupFile = Paths.get(backup_url.toURI());

		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);
	}

	@Test
	public void testWriteTagStringInvalidformat() throws Exception {
		ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get(url.toURI());

		// Check the value is correct at the start
		Map<ExifTool.Tag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("Wrong starting value", "2010:12:10 17:07:05",
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		String newDate = "2egek opkpgrpok";
		Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
		newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);

		// Now change it to an invalid value which should fail
		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check that it's not updated
		metadata = tool
				.getImageMeta(imageFile.toFile(),
						ExifTool.Format.HUMAN_READABLE,
						ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("DateTimeOriginal tag is wrong", "2010:12:10 17:07:05",
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
		URL backup_url = getClass().getResource(
				"/nexus-s-electric-cars.jpg_original");
		// might not exist
		if (backup_url != null) {
			Path backupFile = Paths.get(backup_url.toURI());
			Files.move(backupFile, imageFile,
					StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Test
	public void testWriteTagNumberNonDaemon() throws Exception {
		ExifTool tool = new ExifTool();
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get(url.toURI());

		// Test what orientation value is at the start
		Map<ExifTool.Tag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.ORIENTATION);
		assertEquals("Orientation tag starting value is wrong",
				"Horizontal (normal)", metadata.get(ExifTool.Tag.ORIENTATION));

		// Now change it
		Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
		newValues.put(ExifTool.Tag.ORIENTATION, 3);

		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check the updated value
		metadata = tool.getImageMeta(imageFile.toFile(),
				ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION);
		assertEquals("Orientation tag updated value is wrong", "Rotate 180",
				metadata.get(ExifTool.Tag.ORIENTATION));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
		URL backup_url = getClass().getResource(
				"/nexus-s-electric-cars.jpg_original");
		Path backupFile = Paths.get(backup_url.toURI());

		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

	}

	@Test
	public void testWriteTagNumber() throws Exception {
		ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get(url.toURI());

		// Test what orientation value is at the start
		Map<ExifTool.Tag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.ORIENTATION);
		assertEquals("Orientation tag starting value is wrong",
				"Horizontal (normal)", metadata.get(ExifTool.Tag.ORIENTATION));

		// Now change it
		Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
		newValues.put(ExifTool.Tag.ORIENTATION, 3);

		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check the updated value
		metadata = tool.getImageMeta(imageFile.toFile(),
				ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION);
		assertEquals("Orientation tag updated value is wrong", "Rotate 180",
				metadata.get(ExifTool.Tag.ORIENTATION));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
		URL backup_url = getClass().getResource(
				"/nexus-s-electric-cars.jpg_original");
		Path backupFile = Paths.get(backup_url.toURI());

		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

	}

	@Test
	public void testWriteMulipleTag() throws Exception {
		ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get(url.toURI());

		// Test what orientation value is at the start
		Map<ExifTool.Tag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.ORIENTATION, ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag starting value is wrong",
				"Horizontal (normal)", metadata.get(ExifTool.Tag.ORIENTATION));
		assertEquals("Wrong starting value", "2010:12:10 17:07:05",
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Now change them
		String newDate = "2014:01:23 10:07:05";
		Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
		newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);
		newValues.put(ExifTool.Tag.ORIENTATION, 3);

		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check the updated value
		metadata = tool.getImageMeta(imageFile.toFile(),
				ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION,
				ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag updated value is wrong", "Rotate 180",
				metadata.get(ExifTool.Tag.ORIENTATION));
		assertEquals("DateTimeOriginal tag is wrong", newDate,
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
		URL backup_url = getClass().getResource(
				"/nexus-s-electric-cars.jpg_original");
		Path backupFile = Paths.get(backup_url.toURI());

		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

	}

	@Test
	public void testWriteMulipleTagNonDaemon() throws Exception {
		ExifTool tool = new ExifTool();
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get(url.toURI());

		// Test what orientation value is at the start
		Map<ExifTool.Tag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.ORIENTATION, ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag starting value is wrong",
				"Horizontal (normal)", metadata.get(ExifTool.Tag.ORIENTATION));
		assertEquals("Wrong starting value", "2010:12:10 17:07:05",
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Now change them
		String newDate = "2014:01:23 10:07:05";
		Map<ExifTool.Tag, Object> newValues = new HashMap<ExifTool.Tag, Object>();
		newValues.put(ExifTool.Tag.DATE_TIME_ORIGINAL, newDate);
		newValues.put(ExifTool.Tag.ORIENTATION, 3);

		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check the updated value
		metadata = tool.getImageMeta(imageFile.toFile(),
				ExifTool.Format.HUMAN_READABLE, ExifTool.Tag.ORIENTATION,
				ExifTool.Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag updated value is wrong", "Rotate 180",
				metadata.get(ExifTool.Tag.ORIENTATION));
		assertEquals("DateTimeOriginal tag is wrong", newDate,
				metadata.get(ExifTool.Tag.DATE_TIME_ORIGINAL));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
		URL backup_url = getClass().getResource(
				"/nexus-s-electric-cars.jpg_original");
		Path backupFile = Paths.get(backup_url.toURI());

		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);

	}

	// todo TEST automatic daemon restart by killing perl process
}
