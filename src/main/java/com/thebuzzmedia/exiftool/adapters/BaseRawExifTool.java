package com.thebuzzmedia.exiftool.adapters;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.thebuzzmedia.exiftool.*;

public class BaseRawExifTool implements RawExifTool {

	@Override
	public boolean isFeatureSupported(Feature feature) throws RuntimeException {
		// TODO Auto-generated method stub
		return false;
	}

//	@Override
//	public void startup() {
//		// TODO Auto-generated method stub
//
//	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub

	}

	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isStayOpen() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isRunning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isFeatureEnabled(Feature feature) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public <T> void addImageMetadata(File file, Map<T, Object> values) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public <T> void writeMetadata(WriteOptions options, File file, Map<T, Object> values) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void rebuildMetadata(File file) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void rebuildMetadata(WriteOptions options, File file) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<String, String> getImageMeta(File file, ReadOptions readOptions, String... tags) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> execute(List<String> args) {
		// TODO Auto-generated method stub
		return null;
	}
}
