package com.thebuzzmedia.exiftool;

import java.util.Timer;
import java.util.TimerTask;


/**
 * Class used to represent the {@link TimerTask} used by the internal auto
 * cleanup {@link Timer} to call {@link ExifToolNew3#close()} after a specified
 * interval of inactivity.
 * 
 * @author Riyad Kalla (software@thebuzzmedia.com)
 * @since 1.1
 */
class CleanupTimerTask extends TimerTask {
	private ExifToolNew2 owner;

	public CleanupTimerTask(ExifToolNew2 owner) throws IllegalArgumentException {
		if (owner == null)
			throw new IllegalArgumentException(
					"owner cannot be null and must refer to the ExifToolNew3 instance creating this task.");

		this.owner = owner;
	}

	@Override
	public void run() {
		ExifToolNew3.log.info("\tAuto cleanup task running...");
		owner.close();
	}
}