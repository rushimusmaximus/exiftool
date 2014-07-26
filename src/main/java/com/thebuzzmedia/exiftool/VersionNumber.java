package com.thebuzzmedia.exiftool;

// ================================================================================
/**
 * Version Number used to determine if one version is after another.
 * 
 * @author Matt Gile, msgile
 */
class VersionNumber {
	private final int[] numbers;

	public VersionNumber(String str) {
		String[] versionParts = str.trim().split("\\.");
		this.numbers = new int[versionParts.length];
		for (int i = 0; i < versionParts.length; i++) {
			numbers[i] = Integer.parseInt(versionParts[i]);
		}
	}

	public VersionNumber(int... numbers) {
		this.numbers = numbers;
	}

	public boolean isBeforeOrEqualTo(VersionNumber other) {
		int max = Math.min(this.numbers.length, other.numbers.length);
		for (int i = 0; i < max; i++) {
			if (this.numbers[i] > other.numbers[i]) {
				return false;
			} else if (this.numbers[i] < other.numbers[i]) {
				return true;
			}
		}
		// assume missing number as zero, so if the current process number
		// is more digits it is a higher version
		return this.numbers.length <= other.numbers.length;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (int number : numbers) {
			if (builder.length() > 0) {
				builder.append(".");
			}
			builder.append(number);
		}
		return builder.toString();
	}
}