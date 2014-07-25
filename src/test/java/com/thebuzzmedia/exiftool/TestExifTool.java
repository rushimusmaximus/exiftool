package com.thebuzzmedia.exiftool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TestMetadata
 * <p>
 * 
 * @author Michael Rush (michaelrush@gmail.com)
 * @since Initially created 8/8/13
 */
public class TestExifTool extends TestCase {

	private static final String TEST_FILES_PATH = "src/test/resources";
	private static Logger log = LoggerFactory.getLogger(TestExifTool.class);

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
						log.error("", e);
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

	public boolean runTests(ExifTool tool, String runId) throws IOException {

		Map<ExifTool.Tag, String> metadata;
		File imageFile;
		Set<ExifTool.Tag> keys;
		ExifTool.Tag tag;

		imageFile = new File(TEST_FILES_PATH
				+ "/kureckjones_jett_075_02-cropped.tif");
		metadata = tool.getImageMeta(imageFile, ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.values());
		assertEquals(22, metadata.size());

		keys = metadata.keySet();

		tag = ExifTool.Tag.IMAGE_WIDTH;
		assertTrue(keys.contains(tag));
		assertEquals(728, tag.parseValue(metadata.get(tag)));

		tag = ExifTool.Tag.IMAGE_HEIGHT;
		assertEquals(825, tag.parseValue(metadata.get(tag)));

		tag = ExifTool.Tag.MODEL;
		assertEquals("P 45+", tag.parseValue(metadata.get(tag)));
		log.info(runId + ": finished image 1");

		imageFile = new File(TEST_FILES_PATH + "/nexus-s-electric-cars.jpg");
		metadata = tool.getImageMeta(imageFile, ExifTool.Format.HUMAN_READABLE,
				ExifTool.Tag.values());
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
		log.info(runId + ": finished image 2");
		return true;
	}

	public void testGroupTags() throws Exception {
		ExifTool tool = new ExifTool(ExifTool.Feature.STAY_OPEN);
		try {
			Map<String, String> metadata;
			File f = new File(TEST_FILES_PATH + "/iptc_test-photoshop.jpg");
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

	public void testTag() {
		assertEquals("string value", "John Doe",
				ExifTool.Tag.AUTHOR.parseValue("John Doe"));
		assertEquals("integer value", 200, ExifTool.Tag.ISO.parseValue("200"));
		assertEquals("double value, from fraction", .25,
				ExifTool.Tag.SHUTTER_SPEED.parseValue("1/4"));
		assertEquals("double value, from decimal", .25,
				ExifTool.Tag.SHUTTER_SPEED.parseValue(".25"));
	}

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

	// todo TEST automatic daemon restart by killing perl process
}
