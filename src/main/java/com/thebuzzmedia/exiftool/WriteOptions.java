package com.thebuzzmedia.exiftool;

// ================================================================================
/**
 * All the write options, is immutable, copy on change, fluent style "with"
 * setters.
 */
public class WriteOptions {
	final long runTimeoutMills;
	final boolean deleteBackupFile;

	public WriteOptions() {
		this(0, false);
	}

	private WriteOptions(long runTimeoutMills, boolean deleteBackupFile) {
		this.runTimeoutMills = runTimeoutMills;
		this.deleteBackupFile = deleteBackupFile;
	}

	@Override
	public String toString() {
		return String.format("%s(runTimeOut:%,d deleteBackupFile:%s)",
				getClass().getSimpleName(), runTimeoutMills, deleteBackupFile);
	}

	public WriteOptions withRunTimeoutMills(long mills) {
		return new WriteOptions(mills, deleteBackupFile);
	}

	/**
	 * ExifToolNew3 automatically makes a backup copy a file before writing metadata
	 * tags in the form "file.ext_original", by default this tool will delete
	 * that original file after the writing is done.
	 */
	public WriteOptions withDeleteBackupFile(boolean enabled) {
		return new WriteOptions(runTimeoutMills, enabled);
	}
}