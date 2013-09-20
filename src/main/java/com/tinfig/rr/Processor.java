package com.tinfig.rr;

import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.configuration.ConfigurationException;

public abstract class Processor {
	private static final Font BIG_FONT = new Font(null, Font.BOLD, 24);

	public abstract void start(Frame frame, Settings settings) throws ConfigurationException;

	public abstract void stop(Settings settings) throws ConfigurationException;

	/**
	 * Process one frame of the video stream. The image may be modified.
	 * 
	 * @param frame
	 *            the frame (not <code>null</code>)
	 * @throws ConfigurationException
	 */
	public abstract void process(Frame frame) throws ConfigurationException;

	public static JTextArea createText(Container container) {
		final JTextArea text = new JTextArea();
		container.add(text);
		return text;
	}

	public static JCheckBox createCheckBox(final Container container, final String label, final boolean defaultValue,
			final ItemListener listener) {
		final JCheckBox button = new JCheckBox(label, defaultValue);
		button.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				System.out.println(label + ": " + button.isSelected());
			}
		});
		button.addItemListener(listener);
		container.add(button);

		return button;
	}

	public static JPanel createRadioGroup(final Container container, final String[] labels,
			final ActionListener listener, final String selectedLabel, final String groupLabel) {
		final JPanel radioPanel = new JPanel();
		if (groupLabel != null) {
			final Border border = BorderFactory.createTitledBorder(groupLabel);
			radioPanel.setBorder(border);
		}

		radioPanel.setLayout(new BoxLayout(radioPanel, BoxLayout.X_AXIS));

		final ButtonGroup group = new ButtonGroup();

		final JRadioButton[] buttons = new JRadioButton[labels.length];
		for (int i = 0; i < buttons.length; i++) {
			buttons[i] = new JRadioButton(labels[i]);

			final JRadioButton thisButton = buttons[i];
			final String thisLabel = labels[i];

			thisButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					System.out.println(thisLabel + ": " + thisButton.isSelected());
				}
			});
			thisButton.addActionListener(listener);

			if (thisLabel.equalsIgnoreCase(selectedLabel)) {
				thisButton.setSelected(true);
			}

			radioPanel.add(thisButton);
			group.add(thisButton);
		}

		container.add(radioPanel);
		return radioPanel;
	}

	public static JSlider createSlider(final Container container, final String label, final int min, final int max,
			final int defaultValue, final ChangeListener listener) {
		final JSlider slider = new JSlider(min, max, defaultValue);
		slider.setBorder(BorderFactory.createTitledBorder(label));
		slider.setMajorTickSpacing((max - min) / 5);
		slider.setMinorTickSpacing(slider.getMajorTickSpacing() / 5);
		slider.setPaintTicks(true);
		slider.setPaintLabels(true);

		slider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				System.out.println(label + ": " + Integer.toString(slider.getValue()));
			}
		});
		slider.addChangeListener(listener);

		container.add(slider);

		return slider;
	}

	public static JButton createButton(final Container container, final String label, final ActionListener listener) {
		final JButton button = new JButton(label);
		button.addActionListener(listener);
		container.add(button);
		return button;
	}

	public static JLabel createStatusPanel(Container container, String label, String defaultStatus) {
		JPanel scorePanel = new JPanel();
		scorePanel.setLayout(new FlowLayout(FlowLayout.LEFT));

		JLabel leftLabel = new JLabel(label);
		leftLabel.setFont(BIG_FONT);
		scorePanel.add(leftLabel);

		JLabel rightLabel = new JLabel(defaultStatus);
		rightLabel.setFont(BIG_FONT);
		scorePanel.add(rightLabel);

		container.add(scorePanel);
		return rightLabel;
	}
}
