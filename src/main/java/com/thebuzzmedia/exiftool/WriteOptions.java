package com.thebuzzmedia.exiftool;

// ================================================================================
/**
 * All the write options, is immutable, copy on change, fluent style "with"
 * setters.
 */
public class WriteOptions {
	final long runTimeoutMills;
	final boolean deleteBackupFile;
    final boolean ignoreMinorErrors;
	
	public WriteOptions() {
		this(0, false, false);
	}

	private WriteOptions(long runTimeoutMills, boolean deleteBackupFile, boolean ignoreMinorErrors) {
		this.runTimeoutMills = runTimeoutMills;
		this.deleteBackupFile = deleteBackupFile;
		this.ignoreMinorErrors = ignoreMinorErrors;
	}

	@Override
	public String toString() {
		return String.format("%s(runTimeOut:%,d deleteBackupFile:%s ignoreMinorErrors:%s)",getClass().getSimpleName(),runTimeoutMills,deleteBackupFile,ignoreMinorErrors);
	}

	public WriteOptions withRunTimeoutMills(long mills) {
		return new WriteOptions(mills, deleteBackupFile, ignoreMinorErrors);
	}

	/**
	 * ExifToolNew3 automatically makes a backup copy a file before writing metadata
	 * tags in the form "file.ext_original", by default this tool will delete
	 * that original file after the writing is done.
	 */
	public WriteOptions withDeleteBackupFile(boolean enabled) {
		return new WriteOptions(runTimeoutMills, enabled, ignoreMinorErrors);
	}
	public WriteOptions withIgnoreMinorErrors(boolean enabled) {
      return new WriteOptions(runTimeoutMills,deleteBackupFile, enabled);
    }
}