package com.tinfig.rr;

import java.io.File;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.UIManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.configuration.ConfigurationException;

import com.googlecode.javacv.cpp.opencv_core;
import com.googlecode.javacv.cpp.opencv_core.CvFont;
import com.tinfig.rr.processors.ControlsProcessor;
import com.tinfig.rr.processors.DebugViewProcessor;
import com.tinfig.rr.processors.FlowDetector;
import com.tinfig.rr.processors.FlowTracker;
import com.tinfig.rr.processors.MotionDetector;
import com.tinfig.rr.processors.OpenCvFrameGrabberProcessor;

public class Main {
	public static CvFont FONT = new CvFont();

	public static void main(final String[] args) {
		try {
			UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
			new Main(args).run();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	// Original command line options
	private final String[] args;

	// Parsed command line options
	private int[] devices = new int[] { 0 };
	private File soundsDir = new File("../raleigh-rampage-sounds");
	private boolean keepProcessing = true;

	public Main(final String[] args) throws ConfigurationException {
		this.args = args;
	}

	public void stopProcessing() {
		keepProcessing = false;
	}

	public void run() throws Exception {
		parseArguments(args);

		opencv_core.cvInitFont(FONT, opencv_core.CV_FONT_HERSHEY_PLAIN, 1, 1, 0, 1, opencv_core.CV_AA);

		System.out.println("Loading effects");

		File samplerSettingsFile = new File("sampler.json");
		Settings samplerSettings = Settings.load(samplerSettingsFile);
		final Sampler sampler = new Sampler();
		sampler.start(soundsDir, samplerSettings, devices);

		Timer samplerTimer = new Timer("sampler", true);
		samplerTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				sampler.process();
			}
		}, 0, 50);

		System.out.println("Starting camera processors");

		List<Processor> allProcessors = new ArrayList<>();
		List<Timer> allTimers = new ArrayList<>();
		List<Settings> allSettings = new ArrayList<>();
		Map<Processor, Settings> processorSettings = new HashMap<>();

		// Start a processor pipeline for each camera
		for (int i = 0; i < devices.length; i++) {
			int device = devices[i];

			File settingsFile = new File(device + ".json");
			Settings settings = Settings.load(settingsFile);
			allSettings.add(settings);

			final Frame frame = new Frame();
			frame.setName(Integer.toString(device));

			final Processor[] processors = createProcessors(device, i, sampler);
			for (Processor processor : processors) {
				processor.start(frame, settings);
				allProcessors.add(processor);
				processorSettings.put(processor, settings);
			}

			System.out.println("Started for device " + device);

			Timer timer = new Timer("capture", true);
			allTimers.add(timer);
			timer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					frame.getDebugImages().clear();
					for (Processor p : processors) {
						try {
							p.process(frame);
						} catch (Throwable e) {
							e.printStackTrace();
						}
					}
				}
			}, 0, 1);
		}

		while (keepProcessing) {
			Thread.sleep(500);
		}

		samplerTimer.cancel();
		for (Timer timer : allTimers) {
			timer.cancel();
		}

		sampler.stop(samplerSettings);
		for (Processor processor : allProcessors) {
			processor.stop(processorSettings.get(processor));
		}

		samplerSettings.save();
		for (Settings settings : allSettings) {
			settings.save();
		}

		System.out.println("Exiting");
	}

	private Processor[] createProcessors(int device, int deviceIndex, Sampler sampler) throws ConfigurationException {
		final List<Processor> processors = new ArrayList<Processor>();

		processors.add(new ControlsProcessor(this));
		processors.add(new OpenCvFrameGrabberProcessor(device));
		processors.add(new MotionDetector());
		processors.add(new FlowDetector());
		processors.add(new FlowTracker(deviceIndex, sampler));
		processors.add(new DebugViewProcessor());

		return processors.toArray(new Processor[processors.size()]);
	}

	private void parseArguments(String[] args) throws ParseException, SocketException, UnknownHostException {
		CommandLineParser parser = new PosixParser();

		Options options = new Options();
		options.addOption("d", "device", true, "Camera device number (can be specified multiple times) (default 0)");
		options.addOption("h", "help", false, "Shows help");
		options.addOption("s", "sounds", true, "Directory containing the sounds (raleigh-rampage-sounds repo)");

		CommandLine line = parser.parse(options, args);

		if (line.hasOption("device")) {
			String[] deviceStrings = line.getOptionValues("device");
			devices = new int[deviceStrings.length];
			for (int i = 0; i < deviceStrings.length; i++) {
				devices[i] = Integer.parseInt(deviceStrings[i]);
			}
			System.out.println("Using devices " + Arrays.toString(devices));
		}

		if (line.hasOption("help")) {
			new HelpFormatter().printHelp(getClass().getName(), options);
			System.exit(0);
		}

		if (line.hasOption("sounds")) {
			soundsDir = new File(line.getOptionValue("sounds"));
		}
	}
}
