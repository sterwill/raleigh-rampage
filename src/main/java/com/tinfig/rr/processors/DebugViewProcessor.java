package com.tinfig.rr.processors;

import java.awt.Container;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.JCheckBox;

import com.googlecode.javacv.CanvasFrame;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.tinfig.rr.Frame;
import com.tinfig.rr.Processor;
import com.tinfig.rr.Settings;

public class DebugViewProcessor extends Processor {
	private static final String SHOW_RAW_VIDEO = "debugView.showRawVideo";
	private static final double GAMMA = 1.0;

	private boolean showRawVideo;
	private CanvasFrame videoCanvasFrame;
	private List<CanvasFrame> debugFrames = new ArrayList<CanvasFrame>();

	private JCheckBox showRawVideoCheckbox;

	public DebugViewProcessor() {
	}

	@Override
	public void start(Frame frame, Settings settings) {
		showRawVideo = settings.getBoolean(SHOW_RAW_VIDEO, false);

		// Add to the control frame
		Container container = frame.getArtifact(ControlsProcessor.ARTIFACT_CONTROL_CONTAINER, Container.class);
		showRawVideoCheckbox = createCheckBox(container, "Show raw video image", showRawVideo, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				showRawVideo = ((JCheckBox) e.getSource()).isSelected();
			}
		});

		videoCanvasFrame = newFrame(frame.getName(), "video", new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// Keep the checkbox in sync
				showRawVideoCheckbox.setSelected(false);
			}
		});
	}

	@Override
	public void stop(Settings settings) {
		videoCanvasFrame.setVisible(false);
		videoCanvasFrame.dispose();

		for (CanvasFrame f : debugFrames) {
			f.setVisible(false);
			f.dispose();
		}

		settings.set(SHOW_RAW_VIDEO, showRawVideo);
	}

	@Override
	public void process(Frame frame) {
		if (showRawVideo) {
			if (!videoCanvasFrame.isVisible()) {
				videoCanvasFrame.setVisible(true);
			}
			videoCanvasFrame.showImage(frame.getVideoImage().getBufferedImage(GAMMA));
		} else {
			if (videoCanvasFrame.isVisible()) {
				videoCanvasFrame.setVisible(false);
			}
		}

		showDebugImages(frame);
	}

	private void showDebugImages(Frame frame) {
		// Display the additional images in key order
		Map<String, IplImage> debugImages = frame.getDebugImages();
		List<String> sortedKeys = new ArrayList<>(debugImages.keySet());
		Collections.sort(sortedKeys);

		int i = 0;
		for (String key : sortedKeys) {
			// Might need to create a new frame
			final CanvasFrame canvasFrame;
			if (i > debugFrames.size() - 1) {
				canvasFrame = newFrame(frame.getName(), key, null);
				canvasFrame.setLocation(i * 10, i * 10);
				canvasFrame.setVisible(true);
				debugFrames.add(canvasFrame);
			} else {
				canvasFrame = debugFrames.get(i);
				updateTitle(canvasFrame, frame.getName(), key);
			}

			IplImage image = debugImages.get(key);
			canvasFrame.showImage(image.getBufferedImage(GAMMA));
			i++;
		}

		// Close unused frames
		for (i = sortedKeys.size(); i < debugFrames.size(); i++) {
			CanvasFrame f = debugFrames.get(i);
			f.setVisible(false);
			f.dispose();
			debugFrames.remove(i);
		}
	}

	private void updateTitle(CanvasFrame frame, String prefix, String key) {
		String title = getTitle(prefix, key);
		if (!frame.getTitle().equals(title)) {
			frame.setTitle(title);
		}
	}

	private CanvasFrame newFrame(String prefix, String key, WindowListener windowListener) {
		final CanvasFrame frame = new CanvasFrame(getTitle(prefix, key));
		frame.addWindowListener(windowListener);
		return frame;
	}

	private String getTitle(String prefix, String key) {
		return prefix + ": " + key;
	}
}
