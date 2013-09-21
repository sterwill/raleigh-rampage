package com.tinfig.rr;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class Settings {
	private Map<String, String> values = new HashMap<>();

	private transient File file;

	public Settings(File file) {
		this.file = file;
	}

	public File getFile() {
		return file;
	}

	public static Settings load(File file) throws JsonSyntaxException, JsonIOException {
		Gson gson = createGson();
		try {
			Settings settings = gson.fromJson(new FileReader(file), Settings.class);
			settings.file = file;
			return settings;
		} catch (FileNotFoundException e) {
			return new Settings(file);
		}
	}

	public void save() throws IOException {
		Gson gson = createGson();
		String json = gson.toJson(this);

		FileWriter writer = new FileWriter(file);
		try {
			writer.write(json);
		} finally {
			writer.close();
		}
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		String value = values.get(key);
		if (value == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	public int getInteger(String key, int defaultValue) {
		String value = values.get(key);
		if (value == null) {
			return defaultValue;
		}
		return Integer.parseInt(value);
	}

	public long getLong(String key, long defaultValue) {
		String value = values.get(key);
		if (value == null) {
			return defaultValue;
		}
		return Long.parseLong(value);
	}

	public float getFloat(String key, float defaultValue) {
		String value = values.get(key);
		if (value == null) {
			return defaultValue;
		}
		return Float.parseFloat(value);
	}

	public double getDouble(String key, double defaultValue) {
		String value = values.get(key);
		if (value == null) {
			return defaultValue;
		}
		return Double.parseDouble(value);
	}

	public void set(String key, Object value) {
		values.put(key, value.toString());
	}

	private static Gson createGson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}

}
