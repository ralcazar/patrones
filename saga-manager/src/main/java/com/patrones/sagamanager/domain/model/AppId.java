package com.patrones.sagamanager.domain.model;

public record AppId(String value) {

	private static final String LEGACY_VALUE = "LEGACY";

	public AppId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("AppId must not be blank");
		}
	}

	public static AppId of(String value) {
		return new AppId(value);
	}

	public static AppId legacy() {
		return new AppId(LEGACY_VALUE);
	}

	@Override
	public String toString() {
		return value;
	}
}
