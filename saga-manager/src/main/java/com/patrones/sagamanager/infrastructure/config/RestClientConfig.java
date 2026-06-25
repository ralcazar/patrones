package com.patrones.sagamanager.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
		SagaManagerKafkaProperties.class,
		LegacyPollerProperties.class,
		ServiceClientsProperties.class
})
public class RestClientConfig {

	@Bean
	public RestClient firstServiceRestClient(ServiceClientsProperties properties) {
		return RestClient.builder().baseUrl(properties.getFirstService().getBaseUrl()).build();
	}

	@Bean
	public RestClient secondServiceRestClient(ServiceClientsProperties properties) {
		return RestClient.builder().baseUrl(properties.getSecondService().getBaseUrl()).build();
	}

	@Bean
	public RestClient thirdServiceRestClient(ServiceClientsProperties properties) {
		return RestClient.builder().baseUrl(properties.getThirdService().getBaseUrl()).build();
	}
}
