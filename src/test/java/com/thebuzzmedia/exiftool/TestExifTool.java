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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.thebuzzmedia.exiftool.adapters.ExifToolService;

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

	private static ExifToolService create(Feature... features) {
		return RawExifTool.Factory.create(features);
		// return new ExifToolNew2(features);
		// return new ExifToolNew(features);
	}

	private static ExifToolService create(int timeoutWhenKeepAliveInMillis, Feature... features) {
		return RawExifTool.Factory.create(timeoutWhenKeepAliveInMillis, features);
		// return new ExifToolNew(timeoutWhenKeepAliveInMillis, features);
		// return new ExifToolNew2(timeoutWhenKeepAliveInMillis, features);
	}

	private ExifToolService create(ReadOptions readOptions, Feature... features) {
		return RawExifTool.Factory.create(readOptions, features);
	}

	private ReadOptions options = new ReadOptions();

	@Test
	public void testSingleTool() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			assertTrue(runTests(tool, ""));
		}

		try (ExifToolService tool = create(/* Feature.STAY_OPEN */)) {
			assertTrue(runTests(tool, ""));
		}

		// try (ExifToolService tool = create(Feature.STAY_OPEN)) {
		// tool.startup();
		// assertTrue(runTests(tool, ""));
		// }
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
					log.debug(getName() + ": starting");
					try (ExifToolService tool = create(Feature.STAY_OPEN)) {
						runTests(tool, getName());
					} catch (IOException e) {
						log.error(e.getMessage(), e);
						fail(e.getMessage());
					} catch (URISyntaxException e) {
						log.error(e.getMessage(), e);
						fail(e.getMessage());
					} finally {
						log.debug(getName() + ": finished");
					}
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
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
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
							log.debug("DONE: " + label + " success!");
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
		}
	}

	@Test(expected = IOException.class)
	public void testProcessTimeout() throws Exception {
		try (ExifToolService tool = create(1, Feature.STAY_OPEN)) {
			long start = System.currentTimeMillis();
			runTests(tool, "will fail");
			long end = System.currentTimeMillis();
			fail("should have failed. passed " + (end - start) + " miliseconds.");
		}
	}

	public boolean runTests(ExifToolService tool, String runId) throws IOException, URISyntaxException {
		testFile(tool, "/kureckjones_jett_075_02-cropped.tif", 32, 728, 825, "P 45+", 100, "1/6", 0.16666);
		testFile(tool, "/nexus-s-electric-cars.jpg", 27, 2560, 1920, "Nexus S", 50, "1/64", 0.015625);
		// Map<String, String> rawMetadata = tool.getImageMeta(imageFile, new
		// ReadOptions().withNumericOutput(Format.HUMAN_READABLE));
		// assertEquals(231, rawMetadata.size());
		return true;
	}

	private void testFile(ExifToolService tool, String resource, int size, int width, int height, String model,
			int iso, String shutterRaw, double shutter) throws URISyntaxException, IOException {
		File imageFile = new File(getClass().getResource(resource).toURI());
		Map<MetadataTag, String> metadata = tool.getImageMeta4d(imageFile,
				new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), Tag.values());
		assertEquals(size, metadata.size());
		assertEquals((Integer)width, Tag.IMAGE_WIDTH.getValue(metadata));
		assertEquals((Integer)height, Tag.IMAGE_HEIGHT.getValue(metadata));
		assertEquals(model, Tag.MODEL.getValue(metadata));
		assertEquals((Integer)iso, Tag.ISO.getValue(metadata));
		assertEquals(shutterRaw, Tag.SHUTTER_SPEED.getRawValue(metadata));
		assertEquals(shutter, (Double) Tag.SHUTTER_SPEED.getValue(metadata), 1e-5);
	}

	@Test
	public void testGroupTags() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			Map<String, String> metadata;

			URL url = getClass().getResource("/iptc_test-photoshop.jpg");
			File f = new File(url.toURI());
			metadata = tool.getImageMeta5(f, options, Format.HUMAN_READABLE, TagGroup.IPTC);
			assertEquals(17, metadata.size());
			assertEquals("IPTC Content: Keywords", metadata.get("Keywords"));
			assertEquals("IPTC Status: Copyright Notice", metadata.get("CopyrightNotice"));
			assertEquals("IPTC Content: Description Writer", metadata.get("Writer-Editor"));
			// for (String key : metadata.keySet()){
			// log.info(String.format("\t\t%s: %s", key, metadata.get(key)));
			// }
		}
	}

	@Test
	public void testTag() {
		assertEquals("string value", "John Doe", Tag.AUTHOR.parseValue("John Doe"));
		assertEquals("integer value", (Integer)200, Tag.ISO.parseValue("200"));
		assertEquals("double value, from fraction", (Double).25, Tag.SHUTTER_SPEED.parseValue("1/4"));
		assertEquals("double value, from decimal", (Double).25, Tag.SHUTTER_SPEED.parseValue(".25"));
	}

	@Test
	public void testVersionNumber() {
		assertTrue(new VersionNumber("1.2").isBeforeOrEqualTo(new VersionNumber("1.2.3")));
		assertTrue(new VersionNumber(1, 2).isBeforeOrEqualTo(new VersionNumber("1.2")));
		assertTrue(new VersionNumber(1, 2, 3).isBeforeOrEqualTo(new VersionNumber("1.3")));
		assertTrue(new VersionNumber(1, 2, 3).isBeforeOrEqualTo(new VersionNumber(2, 1)));
	}

	@Test
	public void testWriteTagStringNonDaemon() throws Exception {
		try (ExifToolService tool = create()) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()), imageFile, StandardCopyOption.REPLACE_EXISTING);
			MetadataTag[] tags = { Tag.DATE_TIME_ORIGINAL };

			// Check the value is correct at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags);
			assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Now change it
			String newDate = "2014:01:23 10:07:05";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
			tool.addImageMetadata(imageFile.toFile(), newValues);
			MetadataTag[] tags1 = { Tag.DATE_TIME_ORIGINAL };

			// Finally check that it's updated
			metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags1);
			assertEquals("DateTimeOriginal tag is wrong", newDate, metadata.get(Tag.DATE_TIME_ORIGINAL));
		}
	}

	@Test
	public void testWriteTagString() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()), imageFile, StandardCopyOption.REPLACE_EXISTING);
			MetadataTag[] tags = { Tag.DATE_TIME_ORIGINAL };

			// Check the value is correct at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags);
			assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Now change it
			String newDate = "2014:01:23 10:07:05";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
			tool.addImageMetadata(imageFile.toFile(), newValues);
			MetadataTag[] tags1 = { Tag.DATE_TIME_ORIGINAL };

			// Finally check that it's updated
			metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags1);
			assertEquals("DateTimeOriginal tag is wrong", newDate, metadata.get(Tag.DATE_TIME_ORIGINAL));
		}
	}

	@Test(expected=ExifError.class)
	public void testWriteTagStringInvalidformat() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			Path imageFile = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(getClass().getResource("/nexus-s-electric-cars.jpg").toURI()), imageFile,
					StandardCopyOption.REPLACE_EXISTING);

			// Check the value is correct at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), Tag.DATE_TIME_ORIGINAL);
			String initial = "2010:12:10 17:07:05";
			assertEquals("Wrong starting value", initial, Tag.DATE_TIME_ORIGINAL.getRawValue(metadata));

			String newDate = "2egek opkpgrpok";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
			ExifError error = null;
			try {
				// Now change it to an invalid value which should fail
				tool.addImageMetadata(imageFile.toFile(), newValues);
			} catch (ExifError e) {
				error = e;
			}
			// Finally check that it's not updated
			metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), Tag.DATE_TIME_ORIGINAL);
			assertEquals("DateTimeOriginal tag is wrong", initial, Tag.DATE_TIME_ORIGINAL.getRawValue(metadata));
			throw error; 
		}
	}

	@Test
	public void testWriteTagNumberNonDaemon() throws Exception {
		try (ExifToolService tool = create()) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()), imageFile, StandardCopyOption.REPLACE_EXISTING);
			MetadataTag[] tags = { Tag.ORIENTATION };

			// Test what orientation value is at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags);
			assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)",
					metadata.get(Tag.ORIENTATION));

			// Now change it
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.ORIENTATION, 3);

			tool.addImageMetadata(imageFile.toFile(), newValues);
			MetadataTag[] tags1 = { Tag.ORIENTATION };

			// Finally check the updated value
			metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags1);
			assertEquals("Orientation tag updated value is wrong", "Rotate 180", metadata.get(Tag.ORIENTATION));
		}
	}

	@Test
	public void testWriteTagNumber() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()), imageFile, StandardCopyOption.REPLACE_EXISTING);
			MetadataTag[] tags = { Tag.ORIENTATION };

			// Test what orientation value is at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags);
			assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)",
					metadata.get(Tag.ORIENTATION));

			// Now change it
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.ORIENTATION, 3);

			tool.addImageMetadata(imageFile.toFile(), newValues);
			MetadataTag[] tags1 = { Tag.ORIENTATION };

			// Finally check the updated value
			metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags1);
			assertEquals("Orientation tag updated value is wrong", "Rotate 180", metadata.get(Tag.ORIENTATION));
		}
	}

	@Test
	public void testWriteMulipleTag() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			Path imageFile = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(getClass().getResource("/nexus-s-electric-cars.jpg").toURI()), imageFile,
					StandardCopyOption.REPLACE_EXISTING);
			// Test what orientation value is at the start
			Map<MetadataTag, String> metadata = tool
					.getImageMeta4d(imageFile.toFile(), new ReadOptions().withNumericOutput(Format.HUMAN_READABLE),
							Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL);
			assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)",
					Tag.ORIENTATION.getRawValue(metadata));
			assertEquals("Wrong starting value", "2010:12:10 17:07:05", Tag.DATE_TIME_ORIGINAL.getRawValue(metadata));

			// Now change them
			String newDate = "2014:01:23 10:07:05";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
			newValues.put(Tag.ORIENTATION, 3);

			tool.addImageMetadata(imageFile.toFile(), newValues);

			// Finally check the updated value
			metadata = tool
					.getImageMeta4d(imageFile.toFile(), new ReadOptions().withNumericOutput(Format.HUMAN_READABLE),
							Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL);
			assertEquals("Orientation tag updated value is wrong", "Rotate 180", Tag.ORIENTATION.getRawValue(metadata));
			assertEquals("DateTimeOriginal tag is wrong", newDate, Tag.DATE_TIME_ORIGINAL.getRawValue(metadata));
		}
	}

	@Test
	public void testWriteMulipleTagNonDaemon() throws Exception {
		try (ExifToolService tool = create()) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()), imageFile, StandardCopyOption.REPLACE_EXISTING);
			MetadataTag[] tags = { Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL };

			// Test what orientation value is at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags);
			assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)",
					metadata.get(Tag.ORIENTATION));
			assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Now change them
			String newDate = "2014:01:23 10:07:05";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
			newValues.put(Tag.ORIENTATION, 3);

			tool.addImageMetadata(imageFile.toFile(), newValues);
			MetadataTag[] tags1 = { Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL };

			// Finally check the updated value
			metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags1);
			assertEquals("Orientation tag updated value is wrong", "Rotate 180", metadata.get(Tag.ORIENTATION));
			assertEquals("DateTimeOriginal tag is wrong", newDate, metadata.get(Tag.DATE_TIME_ORIGINAL));
		}
	}

	@Test
	public void testWriteMultipleTagNonDaemon2() throws Exception {
		try (ExifToolService tool = create()) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()), imageFile, StandardCopyOption.REPLACE_EXISTING);
			MetadataTag[] tags = { Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL };

			// Test what orientation value is at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags);
			assertEquals("Orientation tag starting value is wrong", "Horizontal (normal)",
					metadata.get(Tag.ORIENTATION));
			assertEquals("Wrong starting value", "2010:12:10 17:07:05", metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Now change them
			String newDate = "2014:01:23 10:07:05";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
			newValues.put(Tag.ORIENTATION, 3);

			// tool.writeMetadata(imageFile.toFile(), newValues);
			tool.addImageMetadata(imageFile.toFile(), newValues);
			MetadataTag[] tags1 = { Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL };

			// Finally check the updated value
			metadata = tool.getImageMeta4d(imageFile.toFile(),
					new ReadOptions().withNumericOutput(Format.HUMAN_READABLE), tags1);
			assertEquals("Orientation tag updated value is wrong", "Rotate 180", metadata.get(Tag.ORIENTATION));
			assertEquals("DateTimeOriginal tag is wrong", newDate, metadata.get(Tag.DATE_TIME_ORIGINAL));
		}
	}

	@Test
	public void testWritingWithImplicitTypes() throws Exception {
		try (ExifToolService tool = create(new ReadOptions().withNumericOutput(true).withConvertTypes(true),
				Feature.MWG_MODULE)) {
			Path imagePath = Paths.get("target", "nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(getClass().getResource("/nexus-s-electric-cars.jpg").toURI()), imagePath, StandardCopyOption.REPLACE_EXISTING);
			File imageFile = imagePath.toFile();
			// Test what orientation value is at the start
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy:MM:dd hh:mm:ss");

			Map<Object, Object> metadata = tool.getImageMeta2(imageFile, options.withNumericOutput(true),
					Tag.ORIENTATION, MwgTag.DATE_TIME_ORIGINAL);
			assertEquals("Orientation tag starting value is wrong", (Integer)1, Tag.ORIENTATION.getValue(metadata));
			assertEquals("Wrong starting value", formatter.parse("2010:12:10 17:07:05"),
					MwgTag.DATE_TIME_ORIGINAL.getValue(metadata));

			// Now change them
			Map<Object, Object> data = new HashMap<Object, Object>();
			Date dateTimeOrig = formatter.parse("2014:01:23 10:07:05");
			data.put(MwgTag.DATE_TIME_ORIGINAL, dateTimeOrig);
			data.put(Tag.ORIENTATION, 3);
			Date createDate = formatter.parse("2013:02:21 10:07:05");
			data.put(MwgTag.CREATE_DATE, createDate.getTime());
			data.put(MwgTag.KEYWORDS, new String[] { "a", "b", "c" });
			tool.writeMetadata(new WriteOptions().withDeleteBackupFile(false), imageFile, data);

			// Finally check the updated value
			metadata = tool.getImageMeta6(imageFile, options.withNumericOutput(true), Tag.ORIENTATION, imageFile, MwgTag.DATE_TIME_ORIGINAL,
					MwgTag.CREATE_DATE, MwgTag.KEYWORDS);
			assertEquals("Orientation tag updated value is wrong", (Integer)3, Tag.ORIENTATION.getValue(metadata));
			assertEquals("DateTimeOriginal tag is wrong", dateTimeOrig, MwgTag.DATE_TIME_ORIGINAL.getValue(metadata));
			assertEquals("CreateDate tag is wrong", createDate, MwgTag.CREATE_DATE.getValue(metadata));
			assertEquals("Keywords tag is wrong", "a", ((String[]) MwgTag.KEYWORDS.getValue(metadata))[0]);
			List<String> keys = Arrays.asList(((String[]) MwgTag.KEYWORDS.getValue(metadata)));
			assertEquals("Keywords tag is wrong", 3, keys.size());
			assertEquals("Keywords tag is wrong", "a:b:c", Joiner.on(":").join(keys));
		}
	}

	@Test//(expected = ExifError.class)
	public void testReadingUtf8NamesWithStayOpen() throws Exception {
		try (ExifToolService tool = create(new ReadOptions().withNumericOutput(true).withConvertTypes(true),
				Feature.STAY_OPEN)) {
			URL url = getClass().getResource("/20140502_152336_Östliche Zubringerstraße.png");
			File imageFile = new File(url.toURI());
			Map<MetadataTag, String> metadata = tool.getImageMeta3(imageFile, options);
			// should fail on the line before. this is just for breakpoint and retry
			Map<MetadataTag, String> metadata2 = tool.getImageMeta3(imageFile, options);
			assertEquals(21, metadata2.size());
		}
	}

	@Test//(expected = ExifError.class)
	public void testReadingUtf8NamesWithStayOpenWithoutSpaces() throws Exception {
		try (ExifToolService tool = create(new ReadOptions().withNumericOutput(true).withConvertTypes(true),
				Feature.STAY_OPEN)) {
			URL url = getClass().getResource("/20140502_152336_Östliche_Zubringerstraße.png");
			File imageFile = new File(url.toURI());
			// System.out.println(imageFile.getAbsolutePath());
			Map<MetadataTag, String> metadata = tool.getImageMeta3(imageFile, options);
			assertEquals(21, metadata.size());
		}
	}

	@Test
	public void testReadingUtf8NamesWithoutStayOpen() throws Exception {
		try (ExifToolService tool = create(new ReadOptions().withNumericOutput(true).withConvertTypes(true))) {
			URL url = getClass().getResource("/20140502_152336_Östliche Zubringerstraße.png");
			File imageFile = new File(url.toURI());
			// System.out.println(imageFile.getAbsolutePath());
			Map<MetadataTag, String> metadata = tool.getImageMeta3(imageFile, options);
			assertEquals(21, metadata.size());
		}
	}

	@Test
	public void testReadingUtf8NamesWithStayOpenAndWindows() throws Exception {
		try (ExifToolService tool = create(new ReadOptions().withNumericOutput(true).withConvertTypes(true),
				Feature.STAY_OPEN, Feature.WINDOWS)) {
			URL url = getClass().getResource("/20140502_152336_Östliche Zubringerstraße.png");
			File imageFile = new File(url.toURI());
			// System.out.println(imageFile.getAbsolutePath());
			Map<MetadataTag, String> metadata = tool.getImageMeta3(imageFile, options);
			assertEquals(21, metadata.size());
		}
	}

	@Test
	public void testReadingUtf8NamesOnWindows() throws Exception {
		try (ExifToolService tool = create(new ReadOptions().withNumericOutput(true).withConvertTypes(true),
				Feature.STAY_OPEN, Feature.WINDOWS)) {
			URL url = getClass().getResource("/20131231_230955_Strada Frumoasă.png");
			File imageFile = new File(url.toURI());
			// System.out.println(imageFile.getAbsolutePath());
			Map<String, String> metadata1 = tool.getImageMeta(imageFile, options);
			assertEquals(21, metadata1.size());
			// System.out.println(metadata1);
			Map<MetadataTag, String> metadata = tool.getImageMeta3(imageFile, options);
			assertEquals(21, metadata.size());
		}
	}
}
