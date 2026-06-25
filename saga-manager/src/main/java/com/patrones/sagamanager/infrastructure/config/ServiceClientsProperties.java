package com.patrones.sagamanager.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "saga-manager.clients")
public class ServiceClientsProperties {

	@NestedConfigurationProperty
	private ClientEndpoint firstService = new ClientEndpoint();

	@NestedConfigurationProperty
	private ClientEndpoint secondService = new ClientEndpoint();

	@NestedConfigurationProperty
	private ClientEndpoint thirdService = new ClientEndpoint();

	public ClientEndpoint getFirstService() {
		return firstService;
	}

	public void setFirstService(ClientEndpoint firstService) {
		this.firstService = firstService;
	}

	public ClientEndpoint getSecondService() {
		return secondService;
	}

	public void setSecondService(ClientEndpoint secondService) {
		this.secondService = secondService;
	}

	public ClientEndpoint getThirdService() {
		return thirdService;
	}

	public void setThirdService(ClientEndpoint thirdService) {
		this.thirdService = thirdService;
	}

	public static class ClientEndpoint {

		private String baseUrl = "http://localhost:8080";

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}
	}
}
