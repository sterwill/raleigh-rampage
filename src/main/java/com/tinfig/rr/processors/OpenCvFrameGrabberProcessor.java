package com.tinfig.rr.processors;

import org.apache.commons.configuration.ConfigurationException;

import com.googlecode.javacv.FrameGrabber.Exception;
import com.googlecode.javacv.OpenCVFrameGrabber;
import com.googlecode.javacv.cpp.opencv_core;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.tinfig.rr.Frame;
import com.tinfig.rr.Processor;
import com.tinfig.rr.Settings;

public class OpenCvFrameGrabberProcessor extends Processor {

	private final int camera;
	private OpenCVFrameGrabber grabber;

	public OpenCvFrameGrabberProcessor(int camera) {
		this.camera = camera;
	}

	@Override
	public void start(Frame frame, Settings settings) throws ConfigurationException {
		try {
			grabber = new OpenCVFrameGrabber(camera);
			grabber.setFrameRate(60);
			grabber.setImageWidth(600);
			grabber.setImageHeight(400);
			grabber.start();
		} catch (Exception e) {
			throw new ConfigurationException(e);
		}
	}

	@Override
	public void stop(Settings settings) throws ConfigurationException {
		try {
			grabber.stop();
		} catch (Exception e) {
			throw new ConfigurationException(e);
		}
	}

	@Override
	public void process(Frame frame) throws ConfigurationException {
		try {
			IplImage grabbed = grabber.grab();

			IplImage image = frame.getVideoImage();
			if (image == null) {
				image = IplImage.create(grabbed.cvSize(), grabbed.depth(), grabbed.nChannels());
				frame.setVideoImage(image);
			}

			opencv_core.cvCopy(grabbed, image);

		} catch (Exception e) {
			throw new ConfigurationException(e);
		}
	}
}
