package com.thebuzzmedia.exiftool.adapters;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.thebuzzmedia.exiftool.*;

@SuppressWarnings("deprecation")
public class ExifToolService extends RawExifToolAdapter implements Closeable {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ExifToolService.class);

	public ExifToolService(RawExifTool exifTool) {
		super(exifTool);
	}

	// public Map<MetadataTag, String> getImageMeta(File image, Tag... tags) throws IllegalArgumentException,
	// SecurityException, IOException {
	// return getImageMeta(image, Format.NUMERIC, tags);
	// }
	public Map<Object, Object> getImageMeta2(File image, ReadOptions options, MetadataTag... tags)
			throws IllegalArgumentException, SecurityException, IOException {
		Map<String, String> all = getImageMeta(image, options, toKeys(tags));
		return (Map) toMetadataTagKeys(all, tags);
	}

	//
	// public Map<Object, Object> getImageMeta2c(File file, ReadOptions options, MetadataTag... tags)
	// throws IllegalArgumentException, SecurityException, IOException {
	// return getImageMeta6(file, options, tags);
	// }
	//
	// public Map<Object, Object> getImageMeta2b(File image, ReadOptions options, MetadataTag... tags)
	// throws IllegalArgumentException, SecurityException, IOException {
	// return (Map) getImageMeta3(image, options, tags);
	// }

	public Map<MetadataTag, String> getImageMeta3(File image, ReadOptions options, MetadataTag... tags)
			throws IllegalArgumentException, SecurityException, IOException {
		return getImageMeta4d(image, new ReadOptions().withNumericOutput(Format.NUMERIC), tags);
	}

	public Map<MetadataTag, String> getImageMeta4d(File image, ReadOptions options, MetadataTag... tags)
			throws IllegalArgumentException, SecurityException, IOException {
		return toMetadataTagKeys(getImageMeta(image, options, toKeys(tags)), tags);
	}

	//
	// public Map<MetadataTag, String> getImageMeta4c(File file, ReadOptions options, Format format, MetadataTag...
	// tags)
	// throws IllegalArgumentException, SecurityException, IOException {
	// Map<?, ?> result = getImageMeta6(file, options, tags);
	// // since meta tags are passed we will have a proper Map result
	// return (Map) result;
	// }
	//
	// public Map<MetadataTag, String> getImageMeta4b(File image, ReadOptions options, Format format, MetadataTag...
	// tags)
	// throws IllegalArgumentException, SecurityException, IOException {
	// if (tags == null) {
	// tags = new MetadataTag[0];
	// }
	// String[] stringTags = new String[tags.length];
	// int i = 0;
	// for (MetadataTag tag : tags) {
	// stringTags[i++] = tag.getKey();
	// }
	// Map<String, String> result = exifTool.getImageMeta(image, new ReadOptions().withNumericOutput(format)
	// .withShowDuplicates(!true), stringTags);
	// ReadOptions readOptions = new ReadOptions().withConvertTypes(true).withNumericOutput(
	// format.equals(Format.NUMERIC));
	// return (Map) convertToMetadataTags(readOptions, result, tags);
	// // map only known values?
	// // return Tag.toTagMap(result);
	// }

	public Map<String, String> getImageMeta5(File image, ReadOptions options, Format format, TagGroup... tags)
			throws IllegalArgumentException, SecurityException, IOException {
		if (tags == null) {
			tags = new TagGroup[0];
		}
		String[] stringTags = new String[tags.length];
		int i = 0;
		for (TagGroup tag : tags) {
			stringTags[i++] = tag.getValue();
		}
		return exifTool.getImageMeta(image, new ReadOptions().withNumericOutput(format).withShowDuplicates(true),
				stringTags);
	}

	//
	// @Override
	// public Map<String, String> getImageMeta(File file, ReadOptions options, Format format, boolean supressDuplicates,
	// String... tags)
	// throws IOException {
	// ReadOptions options = defReadOptions.withNumericOutput(format == Format.NUMERIC)
	// .withShowDuplicates(!supressDuplicates).withConvertTypes(false);
	// Map<Object, Object> result = getImageMeta7(file, options, tags);
	// Map<String, String> data = new TreeMap<String, String>();
	// for (Map.Entry<Object, Object> entry : result.entrySet()) {
	// data.put(entry.getKey().toString(), entry.getValue() != null ? entry.getValue().toString() : "");
	// }
	// return data;
	// }

	// public Map<MetadataTag, String> getImageMeta(File image, Format format, Tag... tags)
	// throws IllegalArgumentException, SecurityException, IOException {
	// if (tags == null) {
	// tags = new Tag[0];
	// }
	// String[] stringTags = new String[tags.length];
	// int i = 0;
	// for (Tag tag : tags) {
	// stringTags[i++] = tag.getKey();
	// }
	// Map<String, String> result = getImageMeta(image, format, true, stringTags);
	// return Tag.toTagMap(result);
	// }

	// @Override
	// public Map<String, String> getImageMeta5b(File image, ReadOptions options, Format format, TagGroup... tags)
	// throws IllegalArgumentException, SecurityException, IOException {
	// if (tags == null) {
	// tags = new TagGroup[0];
	// }
	// String[] stringTags = new String[tags.length];
	// int i = 0;
	// for (TagGroup tag : tags) {
	// stringTags[i++] = tag.getKey();
	// }
	// return exifTool.getImageMeta(image, options.withNumericOutput(format).withShowDuplicates(false), stringTags);
	// }

	public Map<Object, Object> getImageMeta6(File file, ReadOptions options, Object... tags) throws IOException {
		return getImageMeta7(file, options, tags);
	}

	public Map<Object, Object> getImageMeta7(File file, ReadOptions options, Object... tags) throws IOException {
		if (tags == null) {
			tags = new TagGroup[0];
		}
		Map<String, String> resultMap = exifTool.getImageMeta(file, options, toKeys(tags));
		return convertToMetadataTags(options, resultMap, tags);
	}

	/**
	 * extract image metadata to exiftool's internal xml format.
	 * 
	 * @param input
	 *            the input file
	 * @return command output as xml string
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public String getImageMetadataXml(File input, boolean includeBinary) throws IOException {
		List<String> args = new ArrayList<String>();
		args.add("-X");
		if (includeBinary)
			args.add("-b");
		args.add(input.getAbsolutePath());

		return toResponse(exifTool.execute(args));
	}

	/**
	 * extract image metadata to exiftool's internal xml format.
	 * 
	 * @param input
	 *            the input file
	 * @param output
	 *            the output file
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void getImageMetadataXml(File input, File output, boolean includeBinary) throws IOException {

		String result = getImageMetadataXml(input, includeBinary);

		try (FileWriter w = new FileWriter(output)) {
			w.write(result);
		}
	}

	/**
	 * output icc profile from input to output.
	 * 
	 * @param input
	 *            the input file
	 * @param output
	 *            the output file for icc data
	 * @return the command result from standard output e.g. "1 output files created"
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public String extractImageIccProfile(File input, File output) throws IOException {

		List<String> args = new ArrayList<String>();
		args.add("-icc_profile");
		args.add(input.getAbsolutePath());

		args.add("-o");
		args.add(output.getAbsolutePath());
		return toResponse(exifTool.execute(args));
	}

	/**
	 * Extract thumbnail from the given tag.
	 * 
	 * @param input
	 *            the input file
	 * @param tag
	 *            the tag containing binary data PhotoshopThumbnail or ThumbnailImage
	 * @return the thumbnail file created. it is in the same folder as the input file because of the syntax of exiftool
	 *         and has the suffix ".thumb.jpg"
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public File extractThumbnail(File input, Tag tag) throws IOException {

		List<String> args = new ArrayList<String>();
		String suffix = ".thumb.jpg";
		String thumbname = FilenameUtils.getBaseName(input.getName()) + suffix;

		args.add("-" + tag.getKey());
		args.add(input.getAbsolutePath());
		args.add("-b");
		args.add("-w");
		args.add(suffix);
		String result = toResponse(exifTool.execute(args));
		File thumbnail = new File(input.getParent() + File.separator + thumbname);
		if (!thumbnail.exists())
			throw new IOException("could not create thumbnail: " + result);
		return thumbnail;
	}

	private String[] toKeys(Object... tags) {
		return Lists.transform(Arrays.asList(tags), new Function<Object, String>() {
			@Override
			public String apply(Object tag) {
				if (tag instanceof MetadataTag) {
					return ((MetadataTag) tag).getKey();
				} else {
					return tag.toString();
				}
			}
		}).toArray(new String[0]);
	}

	public static Map<Object, Object> convertToMetadataTags(ReadOptions options, Map<String, String> resultMap,
			Object... tags) {
		Map<Object, Object> metadata = new HashMap<Object, Object>(resultMap.size());

		for (Object tag : tags) {
			MetadataTag metaTag;
			if (tag instanceof MetadataTag) {
				metaTag = (MetadataTag) tag;
			} else {
				metaTag = toTag(tag.toString());
			}
			if (metaTag.isMapped()) {
				String input = resultMap.remove(metaTag.getKey());
				if (!options.showEmptyTags && (input == null || input.isEmpty())) {
					continue;
				}
				Object value = options.convertTypes ? Tag.deserialize(metaTag.getKey(), input, metaTag.getType())
						: input;
				// maps with tag passed in, as caller expects to fetch
				metadata.put(metaTag, value);
			}
		}
		for (Map.Entry<String, String> entry : resultMap.entrySet()) {
			if (!options.showEmptyTags && entry.getValue() == null || entry.getValue().isEmpty()) {
				continue;
			}
			if (options.convertTypes) {
				MetadataTag metaTag = toTag(entry.getKey());
				Object value = Tag.deserialize(metaTag.getKey(), entry.getValue(), metaTag.getType());
				// metadata.put(entry.getKey(), value);
				metadata.put(metaTag, value);
			} else {
				metadata.put(entry.getKey(), entry.getValue());

			}
		}
		return metadata;
	}

	static MetadataTag toTag(String name) {
		// Tag.forName(
		for (Tag tag : Tag.values()) {
			if (tag.getKey().equalsIgnoreCase(name)) {
				return tag;
			}
		}
		for (MwgTag tag : MwgTag.values()) {
			if (tag.getKey().equalsIgnoreCase(name)) {
				return tag;
			}
		}
		return new CustomTag(name, String.class);
	}

	private Map<MetadataTag, String> toMetadataTagKeys(Map<String, String> all, MetadataTag... tags) {
		Map<MetadataTag, String> result = new HashMap<MetadataTag, String>();
		if (tags == null | tags.length == 0) {
			for (Entry<String, String> entry : all.entrySet()) {
				MetadataTag tag = toTag(entry.getKey());
				// if (tag != null) {
				result.put(tag, entry.getValue());
				// }
			}
		} else {
			for (MetadataTag tag : tags) {
				String value = all.get(tag.getKey());
				if (value != null) {
					result.put(tag, value);
				}
			}
		}
		return result;
	}

	private String[] toKeys(MetadataTag... tags) {
		return Lists.transform(Arrays.asList(tags), new Function<MetadataTag, String>() {
			@Override
			public String apply(MetadataTag input) {
				return input.getKey();
			}
		}).toArray(new String[0]);
	}

	//
	// @Override @Deprecated
	// public Map<String, String> getImageMeta(File file, Format format,
	// boolean supressDuplicates, String... tags) throws IOException {
	// return getImageMeta(file,format.withSuppressDuplicates(),tags);
	// }
	//
	// @Override @Deprecated
	// public Map<String, String> getImageMeta(File file, ReadOptions options,
	// boolean supressDuplicates, String... tags) throws IOException {
	// return getImageMeta10(file,options,tags);
	// }

	@Override
	public void close() {
		super.close();
	}

	/**
	 * Compiled {@link Pattern} of ": " used to split compact output from ExifToolNew3 evenly into name/value pairs.
	 */
	private static final Pattern TAG_VALUE_PATTERN = Pattern.compile("\\s*:\\s*");

	public static String toResponse(List<String> results) {
		return Joiner.on('\n').join(results);
	}

	public static Map<String, String> toMap(List<String> all) {
		Map<String, String> resultMap = new HashMap<String, String>(500);
		for (String line : all) {
			String[] pair = TAG_VALUE_PATTERN.split(line, 2);
			if (pair.length == 2) {
				resultMap.put(pair[0], pair[1]);
				LOG.debug(String.format("\tRead Tag [name=%s, value=%s]", pair[0], pair[1]));
			} else {
				LOG.info(String.format("\tIgnore line [%s]", line));
			}
		}
		return resultMap;
	}
}
