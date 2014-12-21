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
import java.util.Date;
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
	private static ExifToolService create(Feature... features){
		return ExifToolService.Factory.create(features);
		//return new ExifToolNew2(features);
		//return new ExifToolNew(features);
	}
	private static ExifToolService create(int timeoutWhenKeepAliveInMillis, Feature... features){
		return ExifToolService.Factory.create(timeoutWhenKeepAliveInMillis, features);
		//return new ExifToolNew(timeoutWhenKeepAliveInMillis, features);
		//return new ExifToolNew2(timeoutWhenKeepAliveInMillis, features);
	}
	private ExifToolService create(ReadOptions readOptions,Feature...features) {
		return ExifToolService.Factory.create(readOptions, features);
	}

	@Test
	public void testSingleTool() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			assertTrue(runTests(tool, ""));
		}

		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			assertTrue(runTests(tool, ""));
		}

		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			tool.startup();
			assertTrue(runTests(tool, ""));
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
					try (ExifToolService tool = create(Feature.STAY_OPEN)) {
						runTests(tool, getName());
						log.info(getName() + ": finished");
					} catch (IOException e) {
						log.error(e.getMessage(), e);
						fail(e.getMessage());
					} catch (URISyntaxException e) {
						log.error(e.getMessage(), e);
						fail(e.getMessage());
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
		}
	}

	@Test
	public void testProcessTimeout() throws Exception {
		try (ExifToolService tool = create(1, Feature.STAY_OPEN)) {
			long start = System.currentTimeMillis();
			runTests(tool, "will fail");
			long end = System.currentTimeMillis();
			fail("should have failed. passed "+(end-start)+" miliseconds.");
		} catch (IOException ex) {
			;
		}
	}

	public boolean runTests(ExifToolService tool, String runId)
			throws IOException, URISyntaxException {

		Map<MetadataTag, String> metadata;
		File imageFile;
		Set<MetadataTag> keys;
		Tag tag;

		URL url = getClass()
				.getResource("/kureckjones_jett_075_02-cropped.tif");
		imageFile = new File(url.toURI());
		metadata = tool.getImageMeta(imageFile, Format.HUMAN_READABLE,
				Tag.values());
		assertEquals(32, metadata.size());

		keys = metadata.keySet();

		tag = Tag.IMAGE_WIDTH;
		assertTrue(keys.contains(tag));
		Object a = metadata.get(tag);
		assertEquals(728, tag.parseValue(metadata.get(tag)));

		tag = Tag.IMAGE_HEIGHT;
		assertEquals(825, tag.parseValue(metadata.get(tag)));

		tag = Tag.MODEL;
		assertEquals("P 45+", tag.parseValue(metadata.get(tag)));
		log.info(runId + ": finished image 1");

		url = getClass().getResource("/nexus-s-electric-cars.jpg");
		imageFile = new File(url.toURI());
		metadata = tool.getImageMeta(imageFile, Format.HUMAN_READABLE,
				Tag.values());
		assertEquals(25, metadata.size());

		keys = metadata.keySet();
		tag = Tag.IMAGE_WIDTH;
		assertTrue(keys.contains(tag));
		assertEquals(2560, tag.parseValue(metadata.get(tag)));

		tag = Tag.IMAGE_HEIGHT;
		assertEquals(1920, tag.parseValue(metadata.get(tag)));

		tag = Tag.MODEL;
		assertEquals("Nexus S", tag.parseValue(metadata.get(tag)));

		tag = Tag.ISO;
		assertEquals(50, tag.parseValue(metadata.get(tag)));

		tag = Tag.SHUTTER_SPEED;
		assertEquals("1/64", metadata.get(tag));
		assertEquals(0.015625, tag.parseValue(metadata.get(tag)));
		log.info(runId + ": finished image 2");
		return true;
	}

	@Test
	public void testGroupTags() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			Map<String, String> metadata;

			URL url = getClass().getResource("/iptc_test-photoshop.jpg");
			File f = new File(url.toURI());
			metadata = tool.getImageMeta(f, Format.HUMAN_READABLE,
					TagGroup.IPTC);
			assertEquals(17, metadata.size());
			assertEquals("IPTC Content: Keywords", metadata.get("Keywords"));
			assertEquals("IPTC Status: Copyright Notice",
					metadata.get("CopyrightNotice"));
			assertEquals("IPTC Content: Description Writer",
					metadata.get("Writer-Editor"));
			// for (String key : metadata.keySet()){
			// log.info(String.format("\t\t%s: %s", key, metadata.get(key)));
			// }
		}
	}

	@Test
	public void testTag() {
		assertEquals("string value", "John Doe",
				Tag.AUTHOR.parseValue("John Doe"));
		assertEquals("integer value", 200, Tag.ISO.parseValue("200"));
		assertEquals("double value, from fraction", .25,
				Tag.SHUTTER_SPEED.parseValue("1/4"));
		assertEquals("double value, from decimal", .25,
				Tag.SHUTTER_SPEED.parseValue(".25"));
	}

	@Test
	public void testVersionNumber() {
		assertTrue(new VersionNumber("1.2")
				.isBeforeOrEqualTo(new VersionNumber("1.2.3")));
		assertTrue(new VersionNumber(1, 2).isBeforeOrEqualTo(new VersionNumber(
				"1.2")));
		assertTrue(new VersionNumber(1, 2, 3)
				.isBeforeOrEqualTo(new VersionNumber("1.3")));
		assertTrue(new VersionNumber(1, 2, 3)
				.isBeforeOrEqualTo(new VersionNumber(2, 1)));
	}

	@Test
	public void testWriteTagStringNonDaemon() throws Exception {
		try (ExifToolService tool = create()) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()),imageFile, StandardCopyOption.REPLACE_EXISTING);

			// Check the value is correct at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta(
					imageFile.toFile(), Format.HUMAN_READABLE,
					Tag.DATE_TIME_ORIGINAL);
			assertEquals("Wrong starting value", "2010:12:10 17:07:05",
					metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Now change it
			String newDate = "2014:01:23 10:07:05";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
			tool.addImageMetadata(imageFile.toFile(), newValues);

			// Finally check that it's updated
			metadata = tool.getImageMeta(imageFile.toFile(),
					Format.HUMAN_READABLE, Tag.DATE_TIME_ORIGINAL);
			assertEquals("DateTimeOriginal tag is wrong", newDate,
					metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Finally copy the source file back over so the next test run is
			// not
			// affected by the change
//			URL backup_url = getClass().getResource(
//					"/nexus-s-electric-cars.jpg_original");
//			Path backupFile = Paths.get(backup_url.toURI());
//
//			Files.move(backupFile, imageFile,
//					StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Test
	public void testWriteTagString() throws Exception {
		ExifToolService tool = create(Feature.STAY_OPEN);
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()),imageFile, StandardCopyOption.REPLACE_EXISTING);

			// Check the value is correct at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta(
					imageFile.toFile(), Format.HUMAN_READABLE,
					Tag.DATE_TIME_ORIGINAL);
			assertEquals("Wrong starting value", "2010:12:10 17:07:05",
					metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Now change it
			String newDate = "2014:01:23 10:07:05";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
			tool.addImageMetadata(imageFile.toFile(), newValues);

			// Finally check that it's updated
			metadata = tool.getImageMeta(imageFile.toFile(),
					Format.HUMAN_READABLE, Tag.DATE_TIME_ORIGINAL);
			assertEquals("DateTimeOriginal tag is wrong", newDate,
					metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Finally copy the source file back over so the next test run is
			// not
			// affected by the change
//			URL backup_url = getClass().getResource(
//					"/nexus-s-electric-cars.jpg_original");
//			Path backupFile = Paths.get(backup_url.toURI());
//			Files.move(backupFile, imageFile,
//					StandardCopyOption.REPLACE_EXISTING);
//		
	}

	@Test
	public void testWriteTagStringInvalidformat() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()),imageFile, StandardCopyOption.REPLACE_EXISTING);

			// Check the value is correct at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta(
					imageFile.toFile(), Format.HUMAN_READABLE,
					Tag.DATE_TIME_ORIGINAL);
			assertEquals("Wrong starting value", "2010:12:10 17:07:05",
					metadata.get(Tag.DATE_TIME_ORIGINAL));

			String newDate = "2egek opkpgrpok";
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);

			// Now change it to an invalid value which should fail
			tool.addImageMetadata(imageFile.toFile(), newValues);

			// Finally check that it's not updated
			metadata = tool.getImageMeta(imageFile.toFile(),
					Format.HUMAN_READABLE, Tag.DATE_TIME_ORIGINAL);
			assertEquals("DateTimeOriginal tag is wrong",
					"2010:12:10 17:07:05", metadata.get(Tag.DATE_TIME_ORIGINAL));

			// Finally copy the source file back over so the next test run is
			// not
			// affected by the change
//			URL backup_url = getClass().getResource(
//					"/nexus-s-electric-cars.jpg_original");
//			// might not exist
//			if (backup_url != null) {
//				Path backupFile = Paths.get(backup_url.toURI());
//				Files.move(backupFile, imageFile,
//						StandardCopyOption.REPLACE_EXISTING);
//			}
		}
	}

	@Test
	public void testWriteTagNumberNonDaemon() throws Exception {
		try (ExifToolService tool = create()) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()),imageFile, StandardCopyOption.REPLACE_EXISTING);

			// Test what orientation value is at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta(
					imageFile.toFile(), Format.HUMAN_READABLE, Tag.ORIENTATION);
			assertEquals("Orientation tag starting value is wrong",
					"Horizontal (normal)", metadata.get(Tag.ORIENTATION));

			// Now change it
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.ORIENTATION, 3);

			tool.addImageMetadata(imageFile.toFile(), newValues);

			// Finally check the updated value
			metadata = tool.getImageMeta(imageFile.toFile(),
					Format.HUMAN_READABLE, Tag.ORIENTATION);
			assertEquals("Orientation tag updated value is wrong",
					"Rotate 180", metadata.get(Tag.ORIENTATION));

			// Finally copy the source file back over so the next test run is
			// not
			// affected by the change
//			URL backup_url = getClass().getResource(
//					"/nexus-s-electric-cars.jpg_original");
//			Path backupFile = Paths.get(backup_url.toURI());
//
//			Files.move(backupFile, imageFile,
//					StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Test
	public void testWriteTagNumber() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
			URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
			Path imageFile = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
			Files.copy(Paths.get(url.toURI()),imageFile, StandardCopyOption.REPLACE_EXISTING);

			// Test what orientation value is at the start
			Map<MetadataTag, String> metadata = tool.getImageMeta(
					imageFile.toFile(), Format.HUMAN_READABLE, Tag.ORIENTATION);
			assertEquals("Orientation tag starting value is wrong",
					"Horizontal (normal)", metadata.get(Tag.ORIENTATION));

			// Now change it
			Map<Tag, Object> newValues = new HashMap<Tag, Object>();
			newValues.put(Tag.ORIENTATION, 3);

			tool.addImageMetadata(imageFile.toFile(), newValues);

			// Finally check the updated value
			metadata = tool.getImageMeta(imageFile.toFile(),
					Format.HUMAN_READABLE, Tag.ORIENTATION);
			assertEquals("Orientation tag updated value is wrong",
					"Rotate 180", metadata.get(Tag.ORIENTATION));

			// Finally copy the source file back over so the next test run is
			// not
			// affected by the change
//			URL backup_url = getClass().getResource(
//					"/nexus-s-electric-cars.jpg_original");
//			Path backupFile = Paths.get(backup_url.toURI());
//
//			Files.move(backupFile, imageFile,
//					StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Test
	public void testWriteMulipleTag() throws Exception {
		try (ExifToolService tool = create(Feature.STAY_OPEN)) {
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
		Files.copy(Paths.get(url.toURI()),imageFile, StandardCopyOption.REPLACE_EXISTING);

		// Test what orientation value is at the start
		Map<MetadataTag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), Format.HUMAN_READABLE, Tag.ORIENTATION,
				Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag starting value is wrong",
				"Horizontal (normal)", metadata.get(Tag.ORIENTATION));
		assertEquals("Wrong starting value", "2010:12:10 17:07:05",
				metadata.get(Tag.DATE_TIME_ORIGINAL));

		// Now change them
		String newDate = "2014:01:23 10:07:05";
		Map<Tag, Object> newValues = new HashMap<Tag, Object>();
		newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
		newValues.put(Tag.ORIENTATION, 3);

		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check the updated value
		metadata = tool.getImageMeta(imageFile.toFile(), Format.HUMAN_READABLE,
				Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag updated value is wrong", "Rotate 180",
				metadata.get(Tag.ORIENTATION));
		assertEquals("DateTimeOriginal tag is wrong", newDate,
				metadata.get(Tag.DATE_TIME_ORIGINAL));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
//		URL backup_url = getClass().getResource(
//				"/nexus-s-electric-cars.jpg_original");
//		Path backupFile = Paths.get(backup_url.toURI());
//
//		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Test
	public void testWriteMulipleTagNonDaemon() throws Exception {
		try (ExifToolService tool = create()) {
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
		Files.copy(Paths.get(url.toURI()),imageFile, StandardCopyOption.REPLACE_EXISTING);

		// Test what orientation value is at the start
		Map<MetadataTag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), Format.HUMAN_READABLE, Tag.ORIENTATION,
				Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag starting value is wrong",
				"Horizontal (normal)", metadata.get(Tag.ORIENTATION));
		assertEquals("Wrong starting value", "2010:12:10 17:07:05",
				metadata.get(Tag.DATE_TIME_ORIGINAL));

		// Now change them
		String newDate = "2014:01:23 10:07:05";
		Map<Tag, Object> newValues = new HashMap<Tag, Object>();
		newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
		newValues.put(Tag.ORIENTATION, 3);

		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check the updated value
		metadata = tool.getImageMeta(imageFile.toFile(), Format.HUMAN_READABLE,
				Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag updated value is wrong", "Rotate 180",
				metadata.get(Tag.ORIENTATION));
		assertEquals("DateTimeOriginal tag is wrong", newDate,
				metadata.get(Tag.DATE_TIME_ORIGINAL));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
//		URL backup_url = getClass().getResource(
//				"/nexus-s-electric-cars.jpg_original");
//		Path backupFile = Paths.get(backup_url.toURI());
//
//		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Test
	public void testWriteMultipleTagNonDaemon2() throws Exception {
		try (ExifToolService tool = create()) {
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imageFile = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
		Files.copy(Paths.get(url.toURI()),imageFile, StandardCopyOption.REPLACE_EXISTING);

		// Test what orientation value is at the start
		Map<MetadataTag, String> metadata = tool.getImageMeta(
				imageFile.toFile(), Format.HUMAN_READABLE, Tag.ORIENTATION,
				Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag starting value is wrong",
				"Horizontal (normal)", metadata.get(Tag.ORIENTATION));
		assertEquals("Wrong starting value", "2010:12:10 17:07:05",
				metadata.get(Tag.DATE_TIME_ORIGINAL));

		// Now change them
		String newDate = "2014:01:23 10:07:05";
		Map<Tag, Object> newValues = new HashMap<Tag, Object>();
		newValues.put(Tag.DATE_TIME_ORIGINAL, newDate);
		newValues.put(Tag.ORIENTATION, 3);

		// tool.writeMetadata(imageFile.toFile(), newValues);
		tool.addImageMetadata(imageFile.toFile(), newValues);

		// Finally check the updated value
		metadata = tool.getImageMeta(imageFile.toFile(), Format.HUMAN_READABLE,
				Tag.ORIENTATION, Tag.DATE_TIME_ORIGINAL);
		assertEquals("Orientation tag updated value is wrong", "Rotate 180",
				metadata.get(Tag.ORIENTATION));
		assertEquals("DateTimeOriginal tag is wrong", newDate,
				metadata.get(Tag.DATE_TIME_ORIGINAL));

		// Finally copy the source file back over so the next test run is not
		// affected by the change
//		URL backup_url = getClass().getResource(
//				"/nexus-s-electric-cars.jpg_original");
//		Path backupFile = Paths.get(backup_url.toURI());
//
//		Files.move(backupFile, imageFile, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	@Test
	public void testWritingWithImplicitTypes() throws Exception {
		try (ExifToolService tool = create(new ReadOptions()
				.withNumericOutput(true).withConvertTypes(true),
				Feature.MWG_MODULE)){
		URL url = getClass().getResource("/nexus-s-electric-cars.jpg");
		Path imagePath = Paths.get("target","nexus-s-electric-cars-tochange.jpg");
		Files.copy(Paths.get(url.toURI()),imagePath, StandardCopyOption.REPLACE_EXISTING);
		File imageFile = imagePath.toFile();
//		try {
			// Test what orientation value is at the start
			SimpleDateFormat formatter = new SimpleDateFormat(
					"yyyy:MM:dd hh:mm:ss");

			Map<Object, Object> metadata = tool.getImageMeta2(imageFile,
					Tag.ORIENTATION, MwgTag.DATE_TIME_ORIGINAL);
			assertEquals("Orientation tag starting value is wrong", 1,
					metadata.get(Tag.ORIENTATION));
			assertEquals("Wrong starting value",
					formatter.parse("2010:12:10 17:07:05"),
					metadata.get(MwgTag.DATE_TIME_ORIGINAL));

			// Now change them
			Map<Object, Object> data = new HashMap<Object, Object>();
			Date dateTimeOrig = formatter.parse("2014:01:23 10:07:05");
			data.put(MwgTag.DATE_TIME_ORIGINAL, dateTimeOrig);
			data.put(Tag.ORIENTATION, 3);
			Date createDate = formatter.parse("2013:02:21 10:07:05");
			data.put(MwgTag.CREATE_DATE, createDate.getTime());
			data.put(MwgTag.KEYWORDS, new String[] { "a", "b", "c" });
			tool.writeMetadata(new WriteOptions().withDeleteBackupFile(false),
					imageFile, data);

			// Finally check the updated value
			metadata = tool.readMetadata(imageFile, Tag.ORIENTATION, imageFile,
					MwgTag.DATE_TIME_ORIGINAL, MwgTag.CREATE_DATE,
					MwgTag.KEYWORDS);
			assertEquals("Orientation tag updated value is wrong", 3,
					metadata.get(Tag.ORIENTATION));
			assertEquals("DateTimeOriginal tag is wrong", dateTimeOrig,
					metadata.get(MwgTag.DATE_TIME_ORIGINAL));
			assertEquals("CreateDate tag is wrong", createDate,
					metadata.get(MwgTag.CREATE_DATE));
			assertEquals("Keywords tag is wrong", "a",
					((String[]) metadata.get(MwgTag.KEYWORDS))[0]);

			// Finally copy the source file back over so the next test run is
			// not affected by the change

//		} finally {
//			URL backup_url = getClass().getResource(
//					"/nexus-s-electric-cars.jpg_original");
//			if (backup_url != null) {
//				Path backupFile = Paths.get(backup_url.toURI());
//				Files.move(backupFile, imageFile.toPath(),
//						StandardCopyOption.REPLACE_EXISTING);
//			}
		
		}
	}

	@Test(expected=ExifError.class)
	public void testReadingUtf8NamesWithStayOpen() throws Exception {
		try (ExifToolService tool = create(
				new ReadOptions().withNumericOutput(true)
						.withConvertTypes(true), Feature.STAY_OPEN)) {
			URL url = getClass().getResource(
					"/20140502_152336_Östliche Zubringerstraße.png");
			File imageFile = new File(url.toURI());
			System.out.println(imageFile.getAbsolutePath());
			Map<MetadataTag, String> metadata = tool.getImageMeta(imageFile);
		}
	}

	@Test
	public void testReadingUtf8NamesWithoutStayOpen() throws Exception {
		try (ExifToolService tool = create(
				new ReadOptions().withNumericOutput(true)
						.withConvertTypes(true))) {
			URL url = getClass().getResource(
					"/20140502_152336_Östliche Zubringerstraße.png");
			File imageFile = new File(url.toURI());
			System.out.println(imageFile.getAbsolutePath());
			Map<MetadataTag, String> metadata = tool.getImageMeta(imageFile);
			assertEquals(19, metadata.size());
		}
	}
	@Test
	public void testReadingUtf8NamesWithStayOpenAndWindows() throws Exception {
		try (ExifToolService tool = create(
				new ReadOptions().withNumericOutput(true)
						.withConvertTypes(true), Feature.STAY_OPEN,Feature.WINDOWS)) {
			URL url = getClass().getResource(
					"/20140502_152336_Östliche Zubringerstraße.png");
			File imageFile = new File(url.toURI());
			System.out.println(imageFile.getAbsolutePath());
			Map<MetadataTag, String> metadata = tool.getImageMeta(imageFile);
			assertEquals(19, metadata.size());
		}
	}
	@Test
	public void testReadingUtf8NamesOnWindows() throws Exception {
		try (ExifToolService tool = create(
				new ReadOptions().withNumericOutput(true)
						.withConvertTypes(true), Feature.STAY_OPEN,Feature.WINDOWS)) {
			URL url = getClass().getResource(
					"/20131231_230955_Strada Frumoasă.png");
			File imageFile = new File(url.toURI());
			System.out.println(imageFile.getAbsolutePath());
			Map<MetadataTag, String> metadata = tool.getImageMeta(imageFile);
			assertEquals(19, metadata.size());
		}
	}
}
