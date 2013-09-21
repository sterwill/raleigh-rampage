package com.tinfig.rr;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;

/**
 * Assertion methods for detecting programming errors at run-time. These methods
 * throw run-time exceptions (not checked exceptions) so they can be used
 * liberally without adding work for the caller.
 * <p>
 * Methods always return the input object unaltered.
 * <p>
 * When a method can throw different kinds of exceptions, the <b>most
 * specific</b> one is thrown. For {@link #notBlank(String, String)}, a
 * <code>null</code> parameter causes a {@link NullPointerException} because you
 * couldn't perform the latter test for "blank".
 */
public abstract class Check {
	/**
	 * Checks that the specified object is not <code>null</code>.
	 */
	public static <T> T notNull(T object, String name) throws NullPointerException {
		if (object == null) {
			throwForNull(object, name);
		}
		return object;
	}

	/**
	 * Checks that the specified array is not <code>null</code> and its length
	 * is > 0.
	 */
	public static <T> T[] notEmpty(T[] array, String name) throws NullPointerException, IllegalArgumentException {
		if (array == null) {
			throwForNull(array, name);
		} else if (array.length == 0) {
			throwForEmpty(array, name);
		}
		return array;
	}

	/**
	 * Checks that the specified collection is not <code>null</code> and its
	 * size is > 0.
	 */
	public static <T> Collection<T> notEmpty(Collection<T> collection, String name) throws NullPointerException,
			IllegalArgumentException {
		if (collection == null) {
			throwForNull(collection, name);
		} else if (collection.size() == 0) {
			throwForEmpty(collection, name);
		}
		return collection;
	}

	/**
	 * Checks that the specified string is not <code>null</code> and has at
	 * least one non-whitespace character in it.
	 */
	public static String notBlank(String string, String name) throws NullPointerException, IllegalArgumentException {
		if (string == null) {
			throwForNull(string, name);
		} else if (StringUtils.isBlank(string)) {
			throwForBlank(string, name);
		}
		return string;
	}

	/**
	 * Throws the specified message if it is <code>false</code>.
	 */
	public static boolean isTrue(boolean condition, String message) throws IllegalArgumentException {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
		return condition;
	}

	private static void throwForNull(Object object, String name) throws NullPointerException {
		throw new NullPointerException(getName(object, name) + " must not be null");
	}

	private static void throwForEmpty(Object object, String name) {
		throw new IllegalArgumentException(getName(object, name) + " must not be empty");
	}

	private static void throwForBlank(Object object, String name) {
		throw new IllegalArgumentException(getName(object, name)
				+ " must contain at least one non-whitespace character");
	}

	private static String getName(Object object, String name) {
		if (name != null && name.length() > 0) {
			return name;
		}

		if (object != null && object.getClass().isArray()) {
			return "array";
		} else if (object instanceof Collection<?>) {
			return "collection";
		} else {
			return "object";
		}
	}
}
