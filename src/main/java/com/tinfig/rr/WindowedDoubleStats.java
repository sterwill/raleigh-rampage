package com.tinfig.rr;

/**
 * Tracks the last n samples and reports on their statistical properties.
 * 
 * @author sterwill
 */
public class WindowedDoubleStats {
	private final double[] samples;
	private final int windowSize;
	private int windowIndex = 0;
	private long totalSamples;

	public WindowedDoubleStats(int windowSize) {
		this.samples = new double[windowSize];
		this.windowSize = windowSize;
	}

	public void add(double sample) {
		samples[windowIndex++] = sample;
		totalSamples++;

		if (windowIndex > windowSize - 1) {
			windowIndex = 0;
		}
	}

	public long getTotalSamples() {
		return totalSamples;
	}

	public double average() {
		double average = 0;
		for (double l : samples) {
			average += l;
		}
		return average / windowSize;
	}

	public double max() {
		double max = Long.MIN_VALUE;
		for (double l : samples) {
			if (l > max) {
				max = l;
			}
		}
		return max;
	}

	public double min() {
		double min = Long.MAX_VALUE;
		for (double l : samples) {
			if (l < min) {
				min = l;
			}
		}
		return min;
	}

	public double first() {
		return windowIndex == 0 ? samples[windowSize - 1] : samples[windowIndex - 1];
	}

	public double last() {
		return samples[windowIndex];
	}

	public double[] toArray() {
		double[] array = new double[windowSize];
		int tgt = array.length - 1;
		int src = windowIndex;
		while (tgt >= 0) {
			array[tgt--] = samples[src++];
			if (src >= samples.length) {
				src = 0;
			}
		}
		return array;
	}
}
