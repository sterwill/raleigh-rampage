package com.tinfig.rr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.tinfig.rr.processors.DebugViewProcessor;

/**
 * Represents the processing state for one frame.
 * 
 * @author sterwill
 */
public class Frame {
	private String name;
	private long timestamp;
	private IplImage videoImage;
	private Map<String, IplImage> computedImages = new HashMap<>();
	private Map<String, IplImage> debugImages = new HashMap<>();
	private Map<String, Object> artifacts = new HashMap<>();

	public Frame() {
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setVideoImage(final IplImage image) {
		videoImage = image;
	}

	public IplImage getVideoImage() {
		return videoImage;
	}

	/**
	 * @return the map of computed images that processors may set so that other
	 *         processors may read and use.
	 */
	public Map<String, IplImage> getComputedImages() {
		return computedImages;
	}

	/**
	 * Gets a list of the computed images entries that start with the specified
	 * prefix.
	 */
	public List<Entry<String, IplImage>> getComputedImages(String prefix) {
		return filterMap(prefix, computedImages, IplImage.class);
	}

	/**
	 * @return the map of images that will be automatically displayed by
	 *         {@link DebugViewProcessor}.
	 */
	public Map<String, IplImage> getDebugImages() {
		return debugImages;
	}

	/**
	 * Gets a list of the debug images entries that start with the specified
	 * prefix.
	 */
	public List<Entry<String, IplImage>> getDebugImages(String prefix) {
		return filterMap(prefix, debugImages, IplImage.class);
	}

	public Map<String, Object> getArtifacts() {
		return artifacts;
	}

	public <T> List<Entry<String, T>> getArtifacts(String prefix, Class<? extends T> clazz) {
		return filterMap(prefix, artifacts, clazz);
	}

	@SuppressWarnings("unchecked")
	public <T> T getArtifact(String name, Class<? extends T> clazz) {
		Object o = artifacts.get(name);
		if (o != null && clazz.isAssignableFrom(o.getClass())) {
			return (T) o;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private <T> List<Entry<String, T>> filterMap(String keyPrefix, Map<String, ?> map, Class<? extends T> clazz) {
		List<Entry<String, T>> ret = new ArrayList<>();
		for (Entry<String, ?> e : map.entrySet()) {
			if (e.getKey().startsWith(keyPrefix) && clazz == e.getValue().getClass()) {
				// Safe to cast because we just checked that it's assignable
				ret.add((Entry<String, T>) e);
			}
		}
		return ret;
	}
}
