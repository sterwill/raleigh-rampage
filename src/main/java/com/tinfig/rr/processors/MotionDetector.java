package com.tinfig.rr.processors;

import java.awt.Container;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.configuration.ConfigurationException;

import com.googlecode.javacpp.Loader;
import com.googlecode.javacpp.Pointer;
import com.googlecode.javacv.cpp.opencv_core;
import com.googlecode.javacv.cpp.opencv_core.CvContour;
import com.googlecode.javacv.cpp.opencv_core.CvMemStorage;
import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.CvSeq;
import com.googlecode.javacv.cpp.opencv_core.CvSize;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_imgproc;
import com.tinfig.rr.Frame;
import com.tinfig.rr.Processor;
import com.tinfig.rr.Settings;

public class MotionDetector extends Processor {
	public static final String ARTIFACT_BUILDING_FEATURES = "rectangle.buildingFeatures";

	private static final String SHOW_CANNY = "rectangle.showCanny";
	private static final String SHOW_CONTOURS = "rectangle.showContours";
	private static final String SHOW_COMPOSITE = "rectangle.showComposite";
	private static final String SHOW_FEATURES = "rectangle.showFeatures";

	private static final String USE_RED_CHANNEL = "rectangle.useRedChannel";
	private static final String USE_GREEN_CHANNEL = "rectangle.useGreenChannel";
	private static final String USE_BLUE_CHANNEL = "rectangle.useBlueChannel";
	private static final String HOUGH_ERODE = "rectangle.erode";
	private static final String HOUGH_DILATE = "rectangle.dilate";
	private static final String CANNY_LOW_THRESHOLD = "rectangle.cannyLowThreshold";
	private static final String CANNY_HIGH_THRESHOLD = "rectangle.cannyHighThreshold";
	private static final String AREA_MIN = "rectangle.areaMin";
	private static final String AREA_MAX = "rectangle.areaMax";
	private static final String EPSILON = "rectangle.epsilon";
	private static final String MAX_COSINE = "rectangle.maxCosine";
	private static final String BLUR = "rectangle.blur";
	private static final String ACCUMULATOR_ALPHA = "rectangle.accumulatorAlpha";
	private static final String HOUGH_THRESHOLD = "rectangle.houghThreshold";
	private static final String HOUGH_MIN_LINE_LENGTH = "rectangle.houghMinLineLength";
	private static final String HOUGH_MAX_GAP_LENGTH = "rectangle.maxGapLength";

	private static final int MAX_CONTOURS = 10000;
	private static final int MAX_LINES = 200;

	// Channel order in color images from OpenCV
	private static final int RED_CHANNEL = 2;
	private static final int GREEN_CHANNEL = 1;
	private static final int BLUE_CHANNEL = 0;

	private IplImage compositeImage;
	private IplImage cannyImage;
	private IplImage contoursImage;
	private IplImage accumulator;
	private IplImage[] channels = new IplImage[3];
	private IplImage[] canny = new IplImage[3];
	private IplImage[] edges = new IplImage[3];
	private IplImage buildingFeatures;

	private boolean useRedChannel = true;
	private boolean useGreenChannel = true;
	private boolean useBlueChannel = true;

	private int cannyLowThreshold = 10;
	private int cannyHighThreshold = 30;

	private int areaMin = 1000;
	private int areaMax = 100000;
	private boolean showCanny;
	private boolean showContours;
	private boolean showComposite;
	private boolean showFeatures;

	private CvMemStorage contourStorage;
	private CvMemStorage polyStorage;
	private CvMemStorage hullStorage;
	private CvMemStorage linesStorage;
	private CvMemStorage minAreaRectStorage;
	private CvSeq contours;

	private int blur = 1;

	private double epsilon = 0.2;
	private double maxCosine = 0.3;
	private double accumulatorAlpha = 0.3;

	private int houghErode = 0;
	private int houghDilate = 1;
	private int houghThreshold = 40;
	private double houghMinLineLength = 20;
	private double houghMaxGapLength = 10;

	@Override
	public void start(Frame frame, Settings settings) throws ConfigurationException {
		showCanny = settings.getBoolean(SHOW_CANNY, showCanny);
		showContours = settings.getBoolean(SHOW_CONTOURS, showContours);
		showComposite = settings.getBoolean(SHOW_COMPOSITE, showComposite);
		showFeatures = settings.getBoolean(SHOW_FEATURES, showFeatures);

		useRedChannel = settings.getBoolean(USE_RED_CHANNEL, useRedChannel);
		useGreenChannel = settings.getBoolean(USE_GREEN_CHANNEL, useGreenChannel);
		useBlueChannel = settings.getBoolean(USE_BLUE_CHANNEL, useBlueChannel);

		cannyLowThreshold = settings.getInteger(CANNY_LOW_THRESHOLD, cannyLowThreshold);
		cannyHighThreshold = settings.getInteger(CANNY_HIGH_THRESHOLD, cannyHighThreshold);
		areaMin = settings.getInteger(AREA_MIN, (int) areaMin);
		areaMax = settings.getInteger(AREA_MAX, (int) areaMax);
		blur = settings.getInteger(BLUR, blur);
		epsilon = settings.getDouble(EPSILON, epsilon);
		maxCosine = settings.getDouble(MAX_COSINE, maxCosine);
		accumulatorAlpha = settings.getDouble(ACCUMULATOR_ALPHA, accumulatorAlpha);

		houghErode = settings.getInteger(HOUGH_ERODE, houghErode);
		houghDilate = settings.getInteger(HOUGH_DILATE, houghDilate);
		houghThreshold = settings.getInteger(HOUGH_THRESHOLD, houghThreshold);
		houghMinLineLength = settings.getDouble(HOUGH_MIN_LINE_LENGTH, houghMinLineLength);
		houghMaxGapLength = settings.getDouble(HOUGH_MAX_GAP_LENGTH, houghMaxGapLength);

		contourStorage = CvMemStorage.create();
		polyStorage = CvMemStorage.create();
		linesStorage = CvMemStorage.create();
		minAreaRectStorage = CvMemStorage.create();
		hullStorage = CvMemStorage.create();
		contours = new CvSeq();

		Container container = frame.getArtifact(ControlsProcessor.ARTIFACT_CONTROL_CONTAINER, Container.class);
		createCheckBox(container, "Show Canny", showCanny, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				showCanny = ((JCheckBox) e.getSource()).isSelected();
			}
		});
		createCheckBox(container, "Show contours", showContours, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				showContours = ((JCheckBox) e.getSource()).isSelected();
			}
		});
		createCheckBox(container, "Show composite", showComposite, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				showComposite = ((JCheckBox) e.getSource()).isSelected();
			}
		});
		createCheckBox(container, "Show features", showFeatures, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				showFeatures = ((JCheckBox) e.getSource()).isSelected();
			}
		});
		createCheckBox(container, "Use red channel", useRedChannel, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				useRedChannel = ((JCheckBox) e.getSource()).isSelected();
			}
		});
		createCheckBox(container, "Use green channel", useGreenChannel, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				useGreenChannel = ((JCheckBox) e.getSource()).isSelected();
			}
		});
		createCheckBox(container, "Use blue channel", useBlueChannel, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				useBlueChannel = ((JCheckBox) e.getSource()).isSelected();
			}
		});
		createSlider(container, "Blur", -1, 6, blur, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				blur = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "Canny low threshold", 1, 255, cannyLowThreshold, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				cannyLowThreshold = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "Canny high threshold", 1, 500, cannyHighThreshold, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				cannyHighThreshold = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "Convex hull area min", 1, 10000, (int) areaMin, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				areaMin = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "Convex hull area max", 1, 100000, (int) areaMax, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				areaMax = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "DRP perimeter factor", 0, 200, (int) (epsilon * 1000), new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				epsilon = ((JSlider) e.getSource()).getValue() / 1000.0;
			}
		});
		createSlider(container, "Max squarish cosine", 0, 100, (int) (maxCosine * 100), new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				maxCosine = ((JSlider) e.getSource()).getValue() / 100.0;
			}
		});
		createSlider(container, "Accumulator alpha", 0, 100, (int) (accumulatorAlpha * 100), new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				accumulatorAlpha = ((JSlider) e.getSource()).getValue() / 100.0;
			}
		});
		createSlider(container, "Hough erode", 0, 10, (int) houghErode, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				houghErode = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "Hough dilate", 0, 10, (int) houghDilate, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				houghDilate = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "Hough threshold", 1, 100, houghThreshold, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				houghThreshold = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "Hough min line length", 1, 300, (int) houghMinLineLength, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				houghMinLineLength = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "Hough max gap length", 1, 50, (int) houghMaxGapLength, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				houghMaxGapLength = ((JSlider) e.getSource()).getValue();
			}
		});

	}

	@Override
	public void stop(Settings settings) throws ConfigurationException {
		settings.set(USE_RED_CHANNEL, useRedChannel);
		settings.set(USE_GREEN_CHANNEL, useGreenChannel);
		settings.set(USE_BLUE_CHANNEL, useBlueChannel);
		settings.set(SHOW_CANNY, showCanny);
		settings.set(SHOW_CONTOURS, showContours);
		settings.set(SHOW_COMPOSITE, showComposite);
		settings.set(SHOW_FEATURES, showFeatures);
		settings.set(CANNY_LOW_THRESHOLD, cannyLowThreshold);
		settings.set(CANNY_HIGH_THRESHOLD, cannyHighThreshold);
		settings.set(AREA_MIN, (int) areaMin);
		settings.set(AREA_MAX, (int) areaMax);
		settings.set(BLUR, blur);
		settings.set(EPSILON, epsilon);
		settings.set(MAX_COSINE, maxCosine);
		settings.set(ACCUMULATOR_ALPHA, accumulatorAlpha);
		settings.set(HOUGH_ERODE, houghErode);
		settings.set(HOUGH_DILATE, houghDilate);
		settings.set(HOUGH_THRESHOLD, houghThreshold);
		settings.set(HOUGH_MIN_LINE_LENGTH, houghMinLineLength);
		settings.set(HOUGH_MAX_GAP_LENGTH, houghMaxGapLength);
	}

	@Override
	public void process(Frame frame) throws ConfigurationException {
		if (compositeImage == null) {
			compositeImage = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 3);
		}

		if (cannyImage == null) {
			cannyImage = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
		} else {
			// Start blank every time
			opencv_core.cvZero(cannyImage);
		}

		if (contoursImage == null) {
			contoursImage = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
		}

		if (channels[0] == null) {
			channels[0] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
			channels[1] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
			channels[2] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
		}

		if (canny[0] == null) {
			canny[0] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
			canny[1] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
			canny[2] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
		}

		if (edges[0] == null) {
			edges[0] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
			edges[1] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
			edges[2] = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
		}

		if (buildingFeatures == null) {
			buildingFeatures = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
		} else {
			// Start blank every time
			opencv_core.cvZero(buildingFeatures);
		}

		// Split frame into color channels
		opencv_core.cvSplit(frame.getVideoImage(), channels[0], channels[1], channels[2], null);

		frame.getArtifacts().put(ARTIFACT_BUILDING_FEATURES, buildingFeatures);
		if (showFeatures) {
			frame.getDebugImages().put("building features", buildingFeatures);
		}

		opencv_core.cvClearMemStorage(contourStorage);
		opencv_core.cvClearMemStorage(polyStorage);
		opencv_core.cvClearMemStorage(hullStorage);
		opencv_core.cvClearMemStorage(linesStorage);
		opencv_core.cvClearMemStorage(minAreaRectStorage);
		if (!contours.isNull()) {
			opencv_core.cvClearSeq(contours);
		}

		if (showComposite) {
			opencv_core.cvCopy(frame.getVideoImage(), compositeImage);
			frame.getDebugImages().put("composite", compositeImage);
		}
		if (showCanny) {
			frame.getDebugImages().put("canny", cannyImage);
		}
		if (showContours) {
			frame.getDebugImages().put("contours", contoursImage);
		}

		// Pre-process each channel
		for (int channel = 0; channel < 3; channel++) {
			if (!isChannelEnabled(channel)) {
				continue;
			}

			if (blur >= 0) {
				opencv_imgproc.GaussianBlur(channels[channel], channels[channel],
						new CvSize(blur * 2 + 1, blur * 2 + 1), 0, 0, opencv_imgproc.BORDER_DEFAULT);
			}
		}

		// Do Canny on each channel
		for (int channel = 0; channel < 3; channel++) {
			if (!isChannelEnabled(channel)) {
				continue;
			}

			// Find edges
			opencv_imgproc.cvCanny(channels[channel], canny[channel], cannyLowThreshold, cannyHighThreshold, 3);

			// Add to the combined Canny image
			opencv_core.cvOr(canny[channel], cannyImage, cannyImage, null);
		}
		// Find contours on Canny image, but copy to a temp image first because
		// find contours modifies the source image.
		opencv_core.cvCopy(cannyImage, contoursImage);
		opencv_imgproc.cvFindContours(contoursImage, contourStorage, contours, Loader.sizeof(CvContour.class),
				opencv_imgproc.CV_RETR_LIST, opencv_imgproc.CV_CHAIN_APPROX_SIMPLE);

		// Find rectangles
		if (!contours.isNull()) {
			if (showComposite && showContours) {
				opencv_core.cvDrawContours(compositeImage, contours, CvScalar.GREEN, CvScalar.GREEN, 1, 1, 0);
			}

			int i = 0;
			CvSeq contour;
			for (contour = contours; contour != null; contour = contour.h_next()) {
				if (i++ > MAX_CONTOURS) {
					break;
				}

				CvSeq poly = opencv_imgproc.cvApproxPoly(contour, Loader.sizeof(CvContour.class), polyStorage,
						opencv_imgproc.CV_POLY_APPROX_DP, opencv_imgproc.cvContourPerimeter(contour) * epsilon, 0);

				if (!poly.isNull() && poly.total() == 4 && opencv_imgproc.cvCheckContourConvexity(poly) == 1) {
					double area = opencv_imgproc.cvContourArea(poly, opencv_core.CV_WHOLE_SEQ, 0);
					if (area >= areaMin && area <= areaMax && maxAngle(poly) < maxCosine) {
						if (showComposite) {
							opencv_core.cvDrawContours(compositeImage, poly, CvScalar.YELLOW, CvScalar.YELLOW, 1, 2, 0);
						}
						opencv_core.cvDrawContours(buildingFeatures, poly, CvScalar.WHITE, CvScalar.WHITE, 1, 1, 0);
					}
				}
			}
		} else {
			System.out.println("Null contours");
		}

		// Find Hough lines on Canny image

		if (houghDilate > 0) {
			opencv_imgproc.cvDilate(cannyImage, cannyImage, null, houghDilate);
		}
		if (houghErode > 0) {
			opencv_imgproc.cvErode(cannyImage, cannyImage, null, houghErode);
		}

		CvSeq lines = opencv_imgproc.cvHoughLines2(cannyImage, linesStorage, opencv_imgproc.CV_HOUGH_PROBABILISTIC, 1,
				Math.PI / 180, houghThreshold, houghMinLineLength, houghMaxGapLength);

		for (int i = 0; i < lines.total() && i < MAX_LINES; i++) {
			Pointer line = opencv_core.cvGetSeqElem(lines, i);
			CvPoint pt1 = new CvPoint(line.position(0));
			CvPoint pt2 = new CvPoint(line.position(1));

			// double slopeRadians = Math.atan((double) (pt2.y() - pt1.y()) /
			// (pt2.x() - pt1.x()));
			// double slopeDegrees = slopeRadians * 180 / Math.PI;
			// if (Math.abs(slopeDegrees) < 80) {
			// continue;
			// }

			if (showComposite && showContours) {
				opencv_core.cvLine(compositeImage, pt1, pt2, CvScalar.BLUE, 2, 0, 0);
			}

			opencv_core.cvLine(buildingFeatures, pt1, pt2, CvScalar.WHITE, 1, 0, 0);
		}

		// Accumulate this frame's rects
		if (accumulator == null) {
			accumulator = IplImage.create(buildingFeatures.cvSize(), opencv_core.IPL_DEPTH_32F, 1);
			opencv_core.cvConvert(buildingFeatures, accumulator);
		} else {
			opencv_imgproc.cvRunningAvg(buildingFeatures, accumulator, accumulatorAlpha, null);
		}

		// Convert back to 8-bit for further processing
		opencv_core.cvConvert(accumulator, buildingFeatures);
	}

	private boolean isChannelEnabled(int channel) {
		switch (channel) {
		case RED_CHANNEL:
			return useRedChannel;
		case GREEN_CHANNEL:
			return useGreenChannel;
		case BLUE_CHANNEL:
			return useBlueChannel;
		}
		throw new RuntimeException("That's not an RGB channel");
	}

	private double maxAngle(CvSeq poly) {
		double maxAngle = 0;
		for (int i = 0; i < 5; i++) {
			// find minimum angle between joint
			// edges (maximum of cosine)
			if (i >= 2) {
				CvPoint p1 = new CvPoint(opencv_core.cvGetSeqElem(poly, i));
				CvPoint p2 = new CvPoint(opencv_core.cvGetSeqElem(poly, i - 2));
				CvPoint p0 = new CvPoint(opencv_core.cvGetSeqElem(poly, i - 1));
				double angle = Math.abs(angle(p1, p2, p0));
				maxAngle = maxAngle > angle ? maxAngle : angle;
			}
		}
		return maxAngle;
	}

	private double angle(CvPoint pt1, CvPoint pt2, CvPoint pt0) {
		double dx1 = pt1.x() - pt0.x();
		double dy1 = pt1.y() - pt0.y();
		double dx2 = pt2.x() - pt0.x();
		double dy2 = pt2.y() - pt0.y();
		double dotProduct = dx1 * dx2 + dy1 * dy2;
		double lengthSquared = (dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2);
		return dotProduct / Math.sqrt(lengthSquared);
	}
}
