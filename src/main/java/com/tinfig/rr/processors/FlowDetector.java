package com.tinfig.rr.processors;

import java.awt.Container;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.configuration.ConfigurationException;

import com.googlecode.javacv.cpp.opencv_core;
import com.googlecode.javacv.cpp.opencv_core.CvPoint;
import com.googlecode.javacv.cpp.opencv_core.CvPoint2D32f;
import com.googlecode.javacv.cpp.opencv_core.CvScalar;
import com.googlecode.javacv.cpp.opencv_core.CvSize;
import com.googlecode.javacv.cpp.opencv_core.CvTermCriteria;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_imgproc;
import com.googlecode.javacv.cpp.opencv_video;
import com.tinfig.rr.Frame;
import com.tinfig.rr.Processor;
import com.tinfig.rr.Settings;

public class FlowDetector extends Processor {
	/**
	 * Integer
	 */
	public static final String ARTIFACT_FLOW_THIS_FRAME = "flow.thisFrame";

	private static final int MAX_NEW_FEATURES = 1000;

	private static final String MIN_TRACKED_POINTS = "flow.minTrackedPoints";
	private static final String Q_LEVEL = "flow.qLevel";
	private static final String MIN_DIST = "flow.minDist";
	private static final String LEARNING_RATE = "flow.learningRate";
	private static final String SHOW_FLOW = "flow.showFlow";

	private int minTrackedPoints = 100;

	private List<CvPoint2D32f> initialPositions = new ArrayList<>();
	private List<CvPoint2D32f> trackedPoints = new ArrayList<>();

	private IplImage currentGray;
	private IplImage previousGray;
	private IplImage debug;

	private boolean showFlow;
	private double qLevel = 0.01;
	private double minDist = 10;

	@Override
	public void start(Frame frame, Settings settings) throws ConfigurationException {
		minTrackedPoints = settings.getInteger(MIN_TRACKED_POINTS, minTrackedPoints);
		qLevel = settings.getDouble(Q_LEVEL, qLevel);
		minDist = settings.getDouble(MIN_DIST, minDist);
		showFlow = settings.getBoolean(SHOW_FLOW, showFlow);

		Container container = frame.getArtifact(ControlsProcessor.ARTIFACT_CONTROL_CONTAINER, Container.class);
		createSlider(container, "Min tracked points", 1, 1000, minTrackedPoints, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				minTrackedPoints = ((JSlider) e.getSource()).getValue();
			}
		});
		createSlider(container, "q level", 1, 500, (int) (qLevel * 1000), new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				qLevel = ((JSlider) e.getSource()).getValue() / 1000.0;
			}
		});
		createSlider(container, "Min dist", 1, 50, (int) minDist, new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				minDist = ((JSlider) e.getSource()).getValue();
			}
		});
		createCheckBox(container, "Show flow", showFlow, new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				showFlow = ((JCheckBox) e.getSource()).isSelected();
			}
		});
	}

	@Override
	public void stop(Settings settings) throws ConfigurationException {
		settings.set(MIN_TRACKED_POINTS, minTrackedPoints);
		settings.set(Q_LEVEL, qLevel);
		settings.set(MIN_DIST, minDist);
		settings.set(SHOW_FLOW, showFlow);
	}

	@Override
	public void process(Frame frame) throws ConfigurationException {
		IplImage rects = frame.getArtifact(MotionDetector.ARTIFACT_BUILDING_FEATURES, IplImage.class);

		if (currentGray == null) {
			currentGray = IplImage.create(frame.getVideoImage().cvSize(), opencv_core.IPL_DEPTH_8U, 1);
		} else {
			opencv_core.cvCopy(rects, currentGray);
		}

		if (debug == null) {
			debug = currentGray.clone();
		} else {
			opencv_core.cvCopy(currentGray, debug);
		}

		// Detect motion of the rectangles

		if (shouldAddNewPoints()) {
			List<CvPoint2D32f> features = detectFeaturePoints(currentGray);
			initialPositions.addAll(features);
			trackedPoints.addAll(features);
		}

		if (previousGray == null) {
			previousGray = currentGray.clone();
			opencv_core.cvCopy(currentGray, previousGray);
		}

		CvPoint2D32f trackedPointsNewUnfilteredOCV = new CvPoint2D32f(trackedPoints.size());
		byte[] trackingStatus = new byte[trackedPoints.size()];

		opencv_video.cvCalcOpticalFlowPyrLK(previousGray, currentGray, null, null, toNativeVector(trackedPoints),
				trackedPointsNewUnfilteredOCV, trackedPoints.size(), new CvSize(21, 21), 3, trackingStatus,
				new float[trackedPoints.size()], new CvTermCriteria(opencv_core.CV_TERMCRIT_ITER
						+ opencv_core.CV_TERMCRIT_EPS, 30, 0.01), 0);

		// 2. loop over the tracked points to reject the undesirables
		List<CvPoint2D32f> trackedPointsNewUnfiltered = toList(trackedPointsNewUnfilteredOCV, Integer.MAX_VALUE);
		List<CvPoint2D32f> initialPositionsNew = new ArrayList<>();
		List<CvPoint2D32f> trackedPointsNew = new ArrayList<>();
		for (int i = 0; i < trackedPointsNewUnfiltered.size(); i++) {
			if (acceptTrackedPoint(trackingStatus[i], trackedPoints.get(i), trackedPointsNewUnfiltered.get(i))) {
				initialPositionsNew.add(initialPositions.get(i));
				trackedPointsNew.add(trackedPointsNewUnfiltered.get(i));
			}
		}

		// 3. handle the accepted tracked points
		trackPoints(initialPositionsNew, trackedPointsNew, frame);

		visualizeTrackedPoints(initialPositionsNew, trackedPointsNew, debug);
		if (showFlow) {
			frame.getDebugImages().put("flow", debug);
		}

		// 4. current points and image become previous ones
		trackedPoints = trackedPointsNew;
		initialPositions = initialPositionsNew;
		opencv_core.cvCopy(currentGray, previousGray);
	}

	private void trackPoints(List<CvPoint2D32f> startPoints, List<CvPoint2D32f> endPoints, Frame frame) {
		int flow = 0;

		if (startPoints.size() >= 0) {
			double sum = 0;
			int movingPoints = 0;

			for (int i = 0; i < startPoints.size(); i++) {
				CvPoint startPoint = opencv_core.cvPointFrom32f(startPoints.get(i));
				CvPoint endPoint = opencv_core.cvPointFrom32f(endPoints.get(i));

				int deltaX = endPoint.x() - startPoint.x();
				int deltaY = endPoint.y() - startPoint.y();

				if (deltaX > 0 || deltaY > 0) {
					movingPoints++;
					flow += Math.round(Math.abs(Math.sqrt(deltaX * deltaX + deltaY * deltaY)));
				}
			}
		}

		frame.getArtifacts().put(ARTIFACT_FLOW_THIS_FRAME, flow);
	}

	private void visualizePoints(List<CvPoint2D32f> points, IplImage debug) {
		for (int i = 0; i < points.size(); i++) {
			CvPoint point = opencv_core.cvPointFrom32f(points.get(i));
			// Mark tracked point movement with aline
			opencv_core.cvDrawCircle(debug, point, 2, CvScalar.BLUE, 1, opencv_core.CV_AA, 0);
		}
	}

	private void visualizeTrackedPoints(List<CvPoint2D32f> startPoints, List<CvPoint2D32f> endPoints, IplImage debug) {
		if (startPoints.size() == 0) {
			return;
		}

		for (int i = 0; i < startPoints.size(); i++) {
			CvPoint startPoint = opencv_core.cvPointFrom32f(startPoints.get(i));
			CvPoint endPoint = opencv_core.cvPointFrom32f(endPoints.get(i));
			// Mark tracked point movement with a line
			opencv_core.cvLine(debug, startPoint, endPoint, CvScalar.WHITE, 1, opencv_core.CV_AA, 0);
			// Mark starting point with circle
			opencv_core.cvCircle(debug, startPoint, 3, CvScalar.WHITE, -1, opencv_core.CV_AA, 0);
		}
	}

	private boolean acceptTrackedPoint(byte status, CvPoint2D32f point0, CvPoint2D32f point1) {
		return status != 0 && (Math.abs(point0.x() - point1.x()) + (Math.abs(point0.y() - point1.y())) > 2);
	}

	private CvPoint2D32f toNativeVector(List<CvPoint2D32f> points) {
		CvPoint2D32f dest = new CvPoint2D32f(points.size());
		for (int i = 0; i < points.size(); i++) {
			dest.position(i).x(points.get(i).x());
			dest.position(i).y(points.get(i).y());
		}
		dest.position(0);
		return dest;
	}

	private CvPoint2D32f featurePoints = new CvPoint2D32f(MAX_NEW_FEATURES);
	private int[] featureCount = new int[] { MAX_NEW_FEATURES };

	private List<CvPoint2D32f> detectFeaturePoints(IplImage grayFrame) {
		featureCount[0] = MAX_NEW_FEATURES;

		opencv_imgproc.cvGoodFeaturesToTrack(grayFrame, null, null, featurePoints, featureCount, qLevel, minDist, null,
				3, 0, 0.04);

		return toList(featurePoints, featureCount[0]);
	}

	private List<CvPoint2D32f> toList(CvPoint2D32f points, int max) {
		int oldPosition = points.position();
		int count = Math.min(points.capacity(), max);

		List<CvPoint2D32f> ret = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			CvPoint2D32f copy = new CvPoint2D32f(1);
			copy.x(points.position(i).x());
			copy.y(points.position(i).y());
			ret.add(copy);
		}

		points.position(oldPosition);
		return ret;
	}

	private boolean shouldAddNewPoints() {
		return trackedPoints.size() < minTrackedPoints;
	}
}
