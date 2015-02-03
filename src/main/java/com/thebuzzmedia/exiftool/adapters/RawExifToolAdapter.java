package com.thebuzzmedia.exiftool.adapters;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.thebuzzmedia.exiftool.*;

public abstract class RawExifToolAdapter implements RawExifTool{
	protected RawExifTool exifTool;

	public RawExifToolAdapter(RawExifTool exifTool) {
		this.exifTool = exifTool;
	}

	@Override
	public boolean isFeatureSupported(Feature feature) throws RuntimeException {
		return exifTool.isFeatureSupported(feature);
	}

//	@Override
//	public void startup() {
//		exifTool.startup();
//	}

	@Override
	public void shutdown() {
		exifTool.shutdown();
	}

	@Override
	public void close() {
		exifTool.close();
	}

	@Override
	public boolean isStayOpen() {
		return exifTool.isStayOpen();
	}

	@Override
	public boolean isRunning() {
		return exifTool.isRunning();
	}

	@Override
	public boolean isFeatureEnabled(Feature feature) throws IllegalArgumentException {
		return exifTool.isFeatureEnabled(feature);
	}

	@Override
	public <T> void addImageMetadata(File file, Map<T, Object> values) throws IOException {
		exifTool.addImageMetadata(file, values);
	}

	@Override
	public <T> void writeMetadata(WriteOptions options, File file, Map<T, Object> values) throws IOException {
		exifTool.writeMetadata(options, file, values);
	}

	@Override
	public void rebuildMetadata(File file) throws IOException {
		exifTool.rebuildMetadata(file);
	}

	@Override
	public void rebuildMetadata(WriteOptions options, File file) throws IOException {
		exifTool.rebuildMetadata(options, file);
	}

	@Override
	public Map<String, String> getImageMeta(File file, ReadOptions readOptions, String... tags) throws IOException {
		return exifTool.getImageMeta(file, readOptions, tags);
	}

	@Override
	public List<String> execute(List<String> args) {
		return exifTool.execute(args);
	}
}
