package com.tinfig.rr.processors;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BoxLayout;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;

import org.apache.commons.configuration.ConfigurationException;

import com.googlecode.javacv.CanvasFrame;
import com.tinfig.rr.Main;
import com.tinfig.rr.Frame;
import com.tinfig.rr.Processor;
import com.tinfig.rr.Settings;

public class ControlsProcessor extends Processor {
	public static final String ARTIFACT_CONTROL_CONTAINER = "controls.controlContainer";

	private Main main;
	private CanvasFrame controlFrame;
	private boolean packed;

	private JPanel controlPanel;

	private JScrollPane scrollPane;

	public ControlsProcessor(Main main) {
		this.main = main;
	}

	@Override
	public void start(Frame frame, Settings settings) throws ConfigurationException {
		controlFrame = new CanvasFrame(frame.getName() + ": " + getClass().getSimpleName());

		controlPanel = new JPanel();
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

		scrollPane = new JScrollPane(controlPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setMinimumSize(new Dimension(400, 800));
		scrollPane.setPreferredSize(new Dimension(400, 800));

		controlFrame.getContentPane().add(scrollPane);

		frame.getArtifacts().put(ARTIFACT_CONTROL_CONTAINER, controlPanel);

		controlFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		controlFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				int confirm = JOptionPane.showOptionDialog(controlFrame, "Are you sure?  The whole thing will stop?",
						"Exit Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
				if (confirm == JOptionPane.YES_OPTION) {
					main.stopProcessing();
				}
			}
		});

		// Other processors may add controls
	}

	@Override
	public void process(Frame frame) throws ConfigurationException {
		if (!packed) {
			controlPanel.setPreferredSize(controlPanel.getLayout().minimumLayoutSize(controlPanel));
			controlFrame.pack();
			packed = true;
		}
	}

	@Override
	public void stop(Settings settings) throws ConfigurationException {
		controlFrame.setVisible(false);
		controlFrame.dispose();
	}
}
