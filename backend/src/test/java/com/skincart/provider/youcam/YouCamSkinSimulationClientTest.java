package com.skincart.provider.youcam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincart.common.AnalysisStatus;
import com.skincart.provider.ProviderException;
import com.skincart.simulation.SkinSimulationFocus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YouCamSkinSimulationClientTest {

    private static final String API_BASE_URL = "https://yce-api-01.makeupar.com";
    private static final String API_TOKEN = "unit-test-token";

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void createsOneDocumentedEffectTaskAndMapsTheResultUrl(CapturedOutput output) {
        var clients = clients();
        clients.server()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/task/skin-simulation"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andExpect(content().json("""
                        {"src_file_id":"file-123","pores":0.3}
                        """, true))
                .andRespond(withSuccess("{\"status\":200,\"data\":{\"task_id\":\"sim-task-1\"}}", MediaType.APPLICATION_JSON));
        clients.server()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/task/skin-simulation/sim-task-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andRespond(withSuccess("{\"status\":200,\"data\":{\"task_status\":\"running\"}}", MediaType.APPLICATION_JSON));
        clients.server()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/task/skin-simulation/sim-task-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"task_status":"success","results":{"url":"https://images.example.test/result.jpg?signature=temp"}}}
                        """, MediaType.APPLICATION_JSON));

        var submission = clients.client().createTask("file-123", SkinSimulationFocus.PORE);
        var running = clients.client().getTask(submission.taskId());
        var completed = clients.client().getTask(submission.taskId());

        assertThat(submission.taskId()).isEqualTo("sim-task-1");
        assertThat(running.status()).isEqualTo(AnalysisStatus.PROCESSING);
        assertThat(completed.status()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(completed.resultImageUrl()).isEqualTo("https://images.example.test/result.jpg?signature=temp");
        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("task creation response: httpStatus=200")
                .contains("YouCam Skin Simulation poll response: httpStatus=200")
                .contains("taskStatus=running")
                .contains("taskStatus=success")
                .doesNotContain("images.example.test", "signature=temp", "url:string");
        clients.server().verify();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void mapsProviderTaskErrorToFailedAndLogsSanitizedDetails(CapturedOutput output) {
        var clients = clients();
        clients.server()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/task/skin-simulation/sim-task-error"))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"task_status":"error","error":"error_no_face",
                         "error_message":"Face processing failed at https://errors.example.test/details?token=private-value"}}
                        """, MediaType.APPLICATION_JSON));

        var response = clients.client().getTask("sim-task-error");

        assertThat(response.status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(response.resultImageUrl()).isNull();
        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("httpStatus=200")
                .contains("providerErrorCode=error_no_face")
                .contains("providerErrorMessage=Face processing failed at [redacted-url]")
                .doesNotContain("errors.example.test", "private-value");
        clients.server().verify();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void mapsRateLimitWithoutAnAutomaticRetryAndLogsOnlySanitizedDetails(CapturedOutput output) {
        var clients = clients();
        clients.server()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/task/skin-simulation"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.RETRY_AFTER, "30")
                        .body("{\"status\":429,\"error_code\":\"error_rate_limit\","
                                + "\"error\":\"See https://errors.example.test/details?signature=private-value\"}"));

        assertThatThrownBy(() -> clients.client().createTask("file-123", SkinSimulationFocus.ACNE))
                .isInstanceOfSatisfying(ProviderException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(exception.getCode()).isEqualTo("PROVIDER_RATE_LIMITED");
                    assertThat(exception.getRetryAfter()).isEqualTo("30");
                });
        String logs = output.getOut() + output.getErr();
        assertThat(logs)
                .contains("httpStatus=429")
                .contains("providerErrorCode=error_rate_limit")
                .contains("providerErrorMessage=See [redacted-url]")
                .doesNotContain("errors.example.test", "private-value");
        clients.server().verify();
    }

    @Test
    void rejectsUndocumentedTaskStatesAndMalformedResultUrls() {
        var stateClients = clients();
        stateClients.server()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/task/skin-simulation/task-1"))
                .andRespond(withSuccess("{\"status\":200,\"data\":{\"task_status\":\"queued\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> stateClients.client().getTask("task-1"))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_MALFORMED_RESPONSE"));
        stateClients.server().verify();

        var urlClients = clients();
        urlClients.server()
                .expect(requestTo(API_BASE_URL + "/s2s/v2.0/task/skin-simulation/task-2"))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"task_status":"success","results":{"url":"javascript:alert(1)"}}}
                        """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> urlClients.client().getTask("task-2"))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("PROVIDER_MALFORMED_RESPONSE"));
        urlClients.server().verify();
    }

    private Clients clients() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(API_BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ObjectMapper objectMapper = new ObjectMapper();
        return new Clients(
                server,
                new YouCamSkinSimulationClient(
                        builder.build(), new YouCamResponseExceptionMapper(objectMapper), objectMapper));
    }

    private record Clients(MockRestServiceServer server, YouCamSkinSimulationClient client) {
    }
}
