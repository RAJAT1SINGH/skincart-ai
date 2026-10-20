package com.skincart.config;

import java.net.http.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincart.provider.youcam.YouCamFileClient;
import com.skincart.provider.youcam.YouCamResponseExceptionMapper;
import com.skincart.provider.youcam.YouCamSkinAnalysisClient;
import com.skincart.provider.youcam.YouCamSkinSimulationClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(YouCamProperties.class)
public class YouCamClientConfiguration {

    @Bean
    YouCamResponseExceptionMapper youCamResponseExceptionMapper(ObjectMapper objectMapper) {
        return new YouCamResponseExceptionMapper(objectMapper);
    }

    @Bean
    YouCamFileClient youCamFileClient(
            @Qualifier("youCamApiRestClient") RestClient apiClient,
            @Qualifier("signedUploadRestClient") RestClient signedUploadClient,
            YouCamResponseExceptionMapper exceptionMapper) {
        return new YouCamFileClient(apiClient, signedUploadClient, exceptionMapper);
    }

    @Bean
    YouCamSkinAnalysisClient youCamSkinAnalysisClient(
            @Qualifier("youCamApiRestClient") RestClient apiClient,
            YouCamResponseExceptionMapper exceptionMapper,
            ObjectMapper objectMapper) {
        return new YouCamSkinAnalysisClient(apiClient, exceptionMapper, objectMapper);
    }

    @Bean
    YouCamSkinSimulationClient youCamSkinSimulationClient(
            @Qualifier("youCamApiRestClient") RestClient apiClient,
            YouCamResponseExceptionMapper exceptionMapper,
            ObjectMapper objectMapper) {
        return new YouCamSkinSimulationClient(apiClient, exceptionMapper, objectMapper);
    }

    @Bean
    ClientHttpRequestFactory youCamRequestFactory(YouCamProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getRequestTimeout());
        return requestFactory;
    }

    @Bean
    RestClient youCamApiRestClient(
            ClientHttpRequestFactory youCamRequestFactory, YouCamProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(youCamRequestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    @Bean
    RestClient signedUploadRestClient(ClientHttpRequestFactory youCamRequestFactory) {
        return RestClient.builder().requestFactory(youCamRequestFactory).build();
    }
}
