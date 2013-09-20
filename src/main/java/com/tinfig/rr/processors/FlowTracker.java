package com.tinfig.rr.processors;

import java.awt.Container;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.tinfig.rr.Frame;
import com.tinfig.rr.Processor;
import com.tinfig.rr.Sampler;
import com.tinfig.rr.Settings;

public class FlowTracker extends Processor {
	public static enum FlowSize {
		// Order from smallest to largest
		NONE, SMALL, MEDIUM, LARGE
	}

	private static final String TRIGGER_SLOPE = "flowTracker.triggerSlope";

	private static final int LONG_SAMPLES = 100;
	private double longAverage = 0;

	private static final int SHORT_SAMPLES = 5;
	private double shortAverage = 0;

	private final int deviceIndex;
	private final Sampler sampler;
	private double smallTriggerSlope = 2;
	private double mediumTriggerSlope = 3;
	private double largeTriggerSlope = 4;

	private long samples;

	private boolean enabled = true;

	public FlowTracker(int deviceIndex, Sampler sampler) {
		this.deviceIndex = deviceIndex;
		this.sampler = sampler;
	}

	@Override
	public void start(Frame frame, Settings settings) {
		smallTriggerSlope = settings.getDouble(TRIGGER_SLOPE, smallTriggerSlope);

		Container container = frame.getArtifact(ControlsProcessor.ARTIFACT_CONTROL_CONTAINER, Container.class);
		createCheckBox(container, "Track flow", enabled, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				enabled = ((JCheckBox) e.getSource()).isSelected();
				if (enabled == false) {
					samples = 0;
					longAverage = 0;
					shortAverage = 0;
				}
			}
		});
		createSlider(container, "Small effect flow trigger", 1, 100, (int) (smallTriggerSlope * 10),
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						smallTriggerSlope = ((JSlider) e.getSource()).getValue() / 10.0;
					}
				});
		createSlider(container, "Medium effect flow trigger", 1, 100, (int) (mediumTriggerSlope * 10),
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						mediumTriggerSlope = ((JSlider) e.getSource()).getValue() / 10.0;
					}
				});
		createSlider(container, "Large effect flow trigger", 1, 100, (int) (largeTriggerSlope * 10),
				new ChangeListener() {
					@Override
					public void stateChanged(ChangeEvent e) {
						largeTriggerSlope = ((JSlider) e.getSource()).getValue() / 10.0;
					}
				});
	}

	@Override
	public void stop(Settings settings) {
		settings.set(TRIGGER_SLOPE, smallTriggerSlope);
	}

	@Override
	public void process(Frame frame) {
		if (!enabled) {
			return;
		}

		int flow = frame.getArtifact(FlowDetector.ARTIFACT_FLOW_THIS_FRAME, Integer.class);

		longAverage = updateMovingAverage(longAverage, LONG_SAMPLES, flow);
		shortAverage = updateMovingAverage(shortAverage, SHORT_SAMPLES, flow);
		samples++;

		// Can't trigger sounds if we don't have a noise floor (longFlow)
		if (samples < LONG_SAMPLES) {
			System.out.println("Training flow tracker");
			return;
		}

		// Calculate the rise in the short term vs. the long term
		double rise = shortAverage / longAverage;

		// Signal any events
		if (rise > largeTriggerSlope) {
			sampler.setDeviceActivity(deviceIndex, FlowSize.LARGE);
		} else if (rise > mediumTriggerSlope) {
			sampler.setDeviceActivity(deviceIndex, FlowSize.MEDIUM);
		} else if (rise > smallTriggerSlope) {
			sampler.setDeviceActivity(deviceIndex, FlowSize.SMALL);
		} else {
			sampler.setDeviceActivity(deviceIndex, FlowSize.NONE);
		}
	}

	/**
	 * Calculates a new simple moving average from the previous value, count of
	 * samples, and one new sample.
	 */
	private double updateMovingAverage(double previous, int window, double newSample) {
		return (previous * window + newSample) / (window + 1);
	}
}
