package com.skincart.provider.youcam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincart.common.AnalysisStatus;
import com.skincart.config.YouCamProperties;
import com.skincart.provider.ProviderException;
import com.skincart.provider.SkinAnalysisImage;
import com.skincart.provider.YouCamSkinAnalysisProvider;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class YouCamSkinAnalysisProviderTest {

    private static final String API_BASE_URL = "https://yce-api-01.makeupar.com";
    private static final String API_TOKEN = "unit-test-token";

    @Test
    void uploadsFileCreatesV21TaskPollsAndMapsResult() {
        Clients clients = clients();
        byte[] imageBytes = new byte[] {1, 2, 3, 4};
        String signedUrl = "https://upload.example.test/path?signature=opaque-value";

        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/file"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andExpect(content().json("""
                        {"files":[{"content_type":"image/png","file_name":"selfie.png","file_size":4}]}
                        """))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"files":[{"content_type":"image/png","file_name":"selfie.png",
                          "file_id":"file-123","requests":[{"method":"PUT","url":"%s",
                          "headers":{"Content-Type":"image/png","Content-Length":"4"}}]}]}}
                        """.formatted(signedUrl), MediaType.APPLICATION_JSON));
        clients.uploadServer()
                .expect(requestTo(signedUrl))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header(HttpHeaders.CONTENT_LENGTH, "4"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(content().bytes(imageBytes))
                .andRespond(withSuccess());
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andExpect(content().json("""
                        {"src_file_id":"file-123","dst_actions":["wrinkle","pore","texture","acne"],"format":"json"}
                        """, true))
                .andRespond(withSuccess(
                        "{\"status\":200,\"data\":{\"task_id\":\"task-456\"}}",
                        MediaType.APPLICATION_JSON));
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-456"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andRespond(withSuccess(
                        "{\"status\":200,\"data\":{\"task_status\":\"running\"}}",
                        MediaType.APPLICATION_JSON));
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-456"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"task_status":"success","results":{"output":[
                          {"type":"wrinkle","ui_score":78,"raw_score":71.23456789,
                           "mask_urls":["https://mask.example.test/wrinkle.png"]},
                          {"type":"pore","ui_score":65,"raw_score":59.25,"mask_urls":[]}
                        ]}}}
                        """, MediaType.APPLICATION_JSON));

        var provider = new YouCamSkinAnalysisProvider(
                properties(), clients.fileClient(), clients.taskClient());
        var submission = provider.submit(new SkinAnalysisImage(imageBytes, "selfie.png", "image/png"));

        assertThat(submission.taskId()).isEqualTo("task-456");
        assertThat(submission.sourceImageReference()).isEqualTo("file-123");
        assertThat(provider.poll(submission.taskId()).status()).isEqualTo(AnalysisStatus.PROCESSING);
        var completed = provider.poll(submission.taskId());
        assertThat(completed.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.result().concerns())
                .containsExactly(
                        new com.skincart.provider.SkinConcernResult(
                                "wrinkle",
                                78,
                                new BigDecimal("71.23456789"),
                                List.of("https://mask.example.test/wrinkle.png")),
                        new com.skincart.provider.SkinConcernResult("pore", 65, new BigDecimal("59.25"), List.of()));
        clients.apiServer().verify();
        clients.uploadServer().verify();
    }

    @Test
    void mapsTaskErrorStatusWithoutInventingProviderStates() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-error"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":200,\"data\":{\"task_status\":\"error\","
                                + "\"error\":\"error_inference\","
                                + "\"error_message\":\"Provider inference failed\"}}",
                        MediaType.APPLICATION_JSON));

        var snapshot = clients.taskClient().getTask("task-error");

        assertThat(snapshot.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(snapshot.result()).isNull();
        clients.apiServer().verify();
    }

    @Test
    void mapsRepresentativeSuccessfulTaskStatusWithMultipleConcerns() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-success"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "status": 200,
                          "data": {
                            "task_status": "success",
                            "results": {
                              "output": [
                                {"type": "texture", "ui_score": 68, "raw_score": 57.33,
                                 "mask_urls": ["https://mask.example.test/texture.png"]},
                                {"type": "pore", "ui_score": 72, "raw_score": 61.125,
                                 "mask_urls": []}
                              ]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var snapshot = clients.taskClient().getTask("task-success");

        assertThat(snapshot.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(snapshot.result().concerns())
                .containsExactly(
                        new com.skincart.provider.SkinConcernResult(
                                "texture", 68, new BigDecimal("57.33"), List.of("https://mask.example.test/texture.png")),
                        new com.skincart.provider.SkinConcernResult(
                                "pore", 72, new BigDecimal("61.125"), List.of()));
        clients.apiServer().verify();
    }

    @Test
    void mapsAllFourSupportedSdConcernOutputs() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-four-concerns"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"task_status":"success","results":{"output":[
                          {"type":"wrinkle","ui_score":78,"raw_score":71.25,"mask_urls":[]},
                          {"type":"pore","ui_score":65,"raw_score":59.5,"mask_urls":[]},
                          {"type":"texture","ui_score":68,"raw_score":57.33,"mask_urls":[]},
                          {"type":"acne","ui_score":82,"raw_score":76.125,"mask_urls":[]}
                        ]}}}
                        """, MediaType.APPLICATION_JSON));

        var snapshot = clients.taskClient().getTask("task-four-concerns");

        assertThat(snapshot.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(snapshot.result().concerns())
                .containsExactly(
                        new com.skincart.provider.SkinConcernResult(
                                "wrinkle", 78, new BigDecimal("71.25"), List.of()),
                        new com.skincart.provider.SkinConcernResult(
                                "pore", 65, new BigDecimal("59.5"), List.of()),
                        new com.skincart.provider.SkinConcernResult(
                                "texture", 68, new BigDecimal("57.33"), List.of()),
                        new com.skincart.provider.SkinConcernResult(
                                "acne", 82, new BigDecimal("76.125"), List.of()));
        clients.apiServer().verify();
    }

    @Test
    void completesMixedSevenOutputResponseAndIgnoresUnsupportedScoreAndMaskEntries() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-mixed"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"task_status":"success","results":{"output":[
                          {"type":"wrinkle","ui_score":78,"raw_score":71.25,
                           "mask_urls":["https://mask.example.test/wrinkle.png"],"url":null},
                          {"type":"pore","ui_score":65,"raw_score":59.5,"mask_urls":[],"url":null},
                          {"type":"texture","ui_score":68,"raw_score":57.33,"mask_urls":[],"url":null},
                          {"type":"acne","ui_score":82,"raw_score":76.125,"mask_urls":[],"url":null},
                          {"type":"unsupported-score-type","score":63.5,"url":null},
                          {"type":"unsupported-score-type","score":42,"url":null},
                          {"type":"unsupported-mask-type","mask_urls":["https://mask.example.test/other.png"],"url":null}
                        ]}}}
                        """, MediaType.APPLICATION_JSON));

        var provider = new YouCamSkinAnalysisProvider(
                properties(), clients.fileClient(), clients.taskClient());
        var snapshot = provider.poll("task-mixed");

        assertThat(snapshot.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(snapshot.result().concerns())
                .containsExactly(
                        new com.skincart.provider.SkinConcernResult(
                                "wrinkle",
                                78,
                                new BigDecimal("71.25"),
                                List.of("https://mask.example.test/wrinkle.png")),
                        new com.skincart.provider.SkinConcernResult(
                                "pore", 65, new BigDecimal("59.5"), List.of()),
                        new com.skincart.provider.SkinConcernResult(
                                "texture", 68, new BigDecimal("57.33"), List.of()),
                        new com.skincart.provider.SkinConcernResult(
                                "acne", 82, new BigDecimal("76.125"), List.of()));
        assertThat(snapshot.result().concerns())
                .noneMatch(concern -> concern.name().startsWith("unsupported-"));
        clients.apiServer().verify();
    }

    @Test
    void doesNotUseProviderScoreWhenSupportedConcernHasNoUiScore() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-no-ui-score"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"task_status":"success","results":{"output":[
                          {"type":"wrinkle","score":78,"raw_score":71.25}
                        ]}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clients.taskClient().getTask("task-no-ui-score"))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_MALFORMED_RESPONSE"));
        clients.apiServer().verify();
    }

    @Test
    void doesNotFabricateRawScoreForSupportedConcern() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-no-raw-score"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"task_status":"success","results":{"output":[
                          {"type":"wrinkle","ui_score":78,"score":71.25}
                        ]}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clients.taskClient().getTask("task-no-raw-score"))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_MALFORMED_RESPONSE"));
        clients.apiServer().verify();
    }

    @Test
    void rejectsMixedSdAndHdConcernConfiguration() {
        Clients clients = clients();
        YouCamProperties properties = properties();
        properties.setConcerns(List.of("wrinkle", "hd_pore"));

        assertThatThrownBy(() -> new YouCamSkinAnalysisProvider(
                        properties, clients.fileClient(), clients.taskClient()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be mixed");
        clients.apiServer().verify();
        clients.uploadServer().verify();
    }

    @ParameterizedTest
    @MethodSource("providerHttpFailures")
    void mapsDocumentedHttpFailures(
            HttpStatus providerStatus, String responseBody, HttpStatus applicationStatus, String applicationCode)
            throws Exception {
        Clients clients = clients();
        var responseCreator = withStatus(providerStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
        if (providerStatus == HttpStatus.TOO_MANY_REQUESTS) {
            responseCreator.header(HttpHeaders.RETRY_AFTER, "30");
        }
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(responseCreator);

        var client = clients.taskClient();
        assertThatThrownBy(() -> client.createTask("file-123", List.of("wrinkle", "pore", "texture", "acne")))
                .isInstanceOfSatisfying(ProviderException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(applicationStatus);
                    assertThat(exception.getCode()).isEqualTo(applicationCode);
                    assertThat(exception.getMessage()).doesNotContain("raw provider message");
                    if (providerStatus == HttpStatus.TOO_MANY_REQUESTS) {
                        assertThat(exception.getRetryAfter()).isEqualTo("30");
                    }
                });
        clients.apiServer().verify();
    }

    @Test
    void rejectsTaskCreationResponseWithoutTaskId() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":200,\"data\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clients.taskClient()
                        .createTask("file-123", List.of("wrinkle", "pore", "texture", "acne")))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_MALFORMED_RESPONSE"));
        clients.apiServer().verify();
    }

    @Test
    void mapsMalformedSuccessfulTaskOutputToProviderException() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.1/task/skin-analysis/task-malformed"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":200,\"data\":{\"task_status\":\"success\",\"results\":{\"output\":[null]}}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clients.taskClient().getTask("task-malformed"))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_MALFORMED_RESPONSE"));
        clients.apiServer().verify();
    }

    @Test
    void mapsTransportTimeoutWithoutMakingNetworkRequest() {
        YouCamResponseExceptionMapper mapper = new YouCamResponseExceptionMapper(new ObjectMapper());

        ProviderException exception = mapper.fromTransport(
                new ResourceAccessException("mocked timeout", new HttpTimeoutException("mocked timeout")));

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(exception.getCode()).isEqualTo("PROVIDER_TIMEOUT");
    }

    @Test
    void mapsSignedUploadFailure() {
        Clients clients = clients();
        String signedUrl = "https://upload.example.test/path?signature=opaque-value";
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/file"))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"files":[{"file_id":"file-123","requests":[
                          {"method":"PUT","url":"%s","headers":{"Content-Type":"image/png","Content-Length":"4"}}
                        ]}]}}
                        """.formatted(signedUrl), MediaType.APPLICATION_JSON));
        clients.uploadServer()
                .expect(requestTo(signedUrl))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> clients.fileClient().uploadImage(
                        new SkinAnalysisImage(new byte[] {1, 2, 3, 4}, "selfie.png", "image/png")))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_UNAVAILABLE"));
        clients.apiServer().verify();
        clients.uploadServer().verify();
    }

    @Test
    void rejectsNonHttpSignedUploadUrlWithoutMakingAnUploadRequest() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/file"))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"files":[{"file_id":"file-123","requests":[
                          {"method":"PUT","url":"file:///tmp/selfie.png","headers":{}}
                        ]}]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clients.fileClient().uploadImage(
                        new SkinAnalysisImage(new byte[] {1, 2, 3, 4}, "selfie.png", "image/png")))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_MALFORMED_RESPONSE"));
        clients.apiServer().verify();
        clients.uploadServer().verify();
    }

    @Test
    void rejectsNullFileEntryAsMalformedMetadataResponse() {
        Clients clients = clients();
        clients.apiServer()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/file"))
                .andRespond(withSuccess("{\"status\":200,\"data\":{\"files\":[null]}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> clients.fileClient().uploadImage(
                        new SkinAnalysisImage(new byte[] {1, 2, 3, 4}, "selfie.png", "image/png")))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_MALFORMED_RESPONSE"));
        clients.apiServer().verify();
        clients.uploadServer().verify();
    }

    private static Stream<Arguments> providerHttpFailures() {
        return Stream.of(
                Arguments.of(
                        HttpStatus.BAD_REQUEST,
                        "{\"status\":400,\"error\":\"raw provider message\",\"error_code\":\"InvalidParameters\"}",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "PROVIDER_INVALID_PARAMETERS"),
                Arguments.of(
                        HttpStatus.BAD_REQUEST,
                        "{\"status\":400,\"error\":\"raw provider message\",\"error_code\":\"error_lighting_dark\"}",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "PROVIDER_IMAGE_REJECTED"),
                Arguments.of(
                        HttpStatus.UNAUTHORIZED,
                        "{\"status\":401,\"error\":\"raw provider message\"}",
                        HttpStatus.BAD_GATEWAY,
                        "PROVIDER_AUTHENTICATION_FAILED"),
                Arguments.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "{\"status\":429,\"error\":\"raw provider message\"}",
                        HttpStatus.TOO_MANY_REQUESTS,
                        "PROVIDER_RATE_LIMITED"),
                Arguments.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "{\"status\":500,\"error\":\"raw provider message\"}",
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "PROVIDER_UNAVAILABLE"));
    }

    private Clients clients() {
        RestClient.Builder apiBuilder = RestClient.builder()
                .baseUrl(API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN);
        MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        RestClient apiClient = apiBuilder.build();

        RestClient.Builder uploadBuilder = RestClient.builder();
        MockRestServiceServer uploadServer = MockRestServiceServer.bindTo(uploadBuilder).build();
        RestClient uploadClient = uploadBuilder.build();

        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        YouCamResponseExceptionMapper exceptionMapper = new YouCamResponseExceptionMapper(objectMapper);
        return new Clients(
                apiServer,
                uploadServer,
                new YouCamFileClient(apiClient, uploadClient, exceptionMapper),
                new YouCamSkinAnalysisClient(apiClient, exceptionMapper, objectMapper));
    }

    private YouCamProperties properties() {
        YouCamProperties properties = new YouCamProperties();
        properties.setApiKey(API_TOKEN);
        return properties;
    }

    private record Clients(
            MockRestServiceServer apiServer,
            MockRestServiceServer uploadServer,
            YouCamFileClient fileClient,
            YouCamSkinAnalysisClient taskClient) {
    }
}
