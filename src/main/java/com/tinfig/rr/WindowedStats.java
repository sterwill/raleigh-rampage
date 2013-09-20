package com.tinfig.rr;

/**
 * Tracks the last n samples and reports on their statistical properties.
 * 
 * @author sterwill
 */
public class WindowedStats {
	private final long[] samples;
	private final int windowSize;
	private int windowIndex = 0;
	private long totalSamples;

	public WindowedStats(int windowSize) {
		this.samples = new long[windowSize];
		this.windowSize = windowSize;
	}

	public void add(long sample) {
		samples[windowIndex++] = sample;
		totalSamples++;

		if (windowIndex > windowSize - 1) {
			windowIndex = 0;
		}
	}

	public long getTotalSamples() {
		return totalSamples;
	}

	public long sum() {
		long sum = 0;
		for (long l : samples) {
			sum += l;
		}
		return sum;
	}

	public long average() {
		long average = 0;
		for (long l : samples) {
			average += l;
		}
		return average / windowSize;
	}

	public long max() {
		long max = Long.MIN_VALUE;
		for (long l : samples) {
			if (l > max) {
				max = l;
			}
		}
		return max;
	}

	public long min() {
		long min = Long.MAX_VALUE;
		for (long l : samples) {
			if (l < min) {
				min = l;
			}
		}
		return min;
	}

	public long first() {
		return windowIndex == 0 ? samples[windowSize - 1] : samples[windowIndex - 1];
	}

	public long last() {
		return samples[windowIndex];
	}
}
