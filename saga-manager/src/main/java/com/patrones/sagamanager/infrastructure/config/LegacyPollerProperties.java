package com.patrones.sagamanager.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saga-manager.legacy-poller")
public class LegacyPollerProperties {

	private boolean enabled = true;
	private long fixedDelayMs = 5000;
	private int pageSize = 20;
	private long leaseSeconds = 30;
	private String successStatus = "PROCESADO";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public long getFixedDelayMs() {
		return fixedDelayMs;
	}

	public void setFixedDelayMs(long fixedDelayMs) {
		this.fixedDelayMs = fixedDelayMs;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public long getLeaseSeconds() {
		return leaseSeconds;
	}

	public void setLeaseSeconds(long leaseSeconds) {
		this.leaseSeconds = leaseSeconds;
	}

	public String getSuccessStatus() {
		return successStatus;
	}

	public void setSuccessStatus(String successStatus) {
		this.successStatus = successStatus;
	}
}
