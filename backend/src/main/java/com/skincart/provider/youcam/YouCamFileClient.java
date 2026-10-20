package com.skincart.provider.youcam;

import com.skincart.provider.ProviderException;
import com.skincart.provider.SkinAnalysisImage;
import com.skincart.provider.youcam.dto.YouCamFileMetadataRequest;
import com.skincart.provider.youcam.dto.YouCamFileMetadataResponse;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class YouCamFileClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(YouCamFileClient.class);
    private static final String FILE_ENDPOINT = "/s2s/v2.0/file";

    private final RestClient apiClient;
    private final RestClient signedUploadClient;
    private final YouCamResponseExceptionMapper exceptionMapper;

    public YouCamFileClient(
            RestClient apiClient,
            RestClient signedUploadClient,
            YouCamResponseExceptionMapper exceptionMapper) {
        this.apiClient = apiClient;
        this.signedUploadClient = signedUploadClient;
        this.exceptionMapper = exceptionMapper;
    }

    public String uploadImage(SkinAnalysisImage image) {
        YouCamFileMetadataResponse response = requestUpload(image);
        YouCamFileMetadataResponse.FileData file = firstFile(response);
        YouCamFileMetadataResponse.UploadRequest uploadRequest = firstPutRequest(file);
        int uploadHttpStatus = uploadBytes(uploadRequest, image.bytes());
        LOGGER.info("YouCam file upload completed: httpStatus={}", uploadHttpStatus);
        return file.fileId();
    }

    private YouCamFileMetadataResponse requestUpload(SkinAnalysisImage image) {
        YouCamFileMetadataRequest request = new YouCamFileMetadataRequest(List.of(
                new YouCamFileMetadataRequest.FileMetadata(
                        image.contentType(), image.fileName(), image.bytes().length)));
        try {
            LOGGER.info("YouCam File API metadata request started");
            ResponseEntity<YouCamFileMetadataResponse> entity = apiClient.post()
                    .uri(FILE_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toEntity(YouCamFileMetadataResponse.class);
            YouCamFileMetadataResponse response = entity.getBody();
            LOGGER.info(
                    "YouCam File API metadata response: httpStatus={}, providerResponseStatus={}",
                    entity.getStatusCode().value(),
                    response == null ? "missing" : response.status());
            if (response == null || !Integer.valueOf(200).equals(response.status())) {
                throw malformedResponse();
            }
            return response;
        } catch (RestClientResponseException exception) {
            LOGGER.warn("YouCam File API metadata request failed: httpStatus={}", exception.getStatusCode().value());
            throw exceptionMapper.fromResponse(exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "YouCam File API metadata request failed before receiving an HTTP response: transportError={}",
                    exception.getClass().getSimpleName());
            throw exceptionMapper.fromTransport(exception);
        }
    }

    private YouCamFileMetadataResponse.FileData firstFile(YouCamFileMetadataResponse response) {
        if (response.data() == null || response.data().files() == null || response.data().files().isEmpty()) {
            throw malformedResponse();
        }
        YouCamFileMetadataResponse.FileData file = response.data().files().getFirst();
        if (file == null || file.fileId() == null || file.fileId().isBlank()) {
            throw malformedResponse();
        }
        return file;
    }

    private YouCamFileMetadataResponse.UploadRequest firstPutRequest(
            YouCamFileMetadataResponse.FileData file) {
        if (file.requests() == null || file.requests().isEmpty()) {
            throw malformedResponse();
        }
        YouCamFileMetadataResponse.UploadRequest request = file.requests().getFirst();
        if (request == null
                || request.method() == null
                || !request.method().equalsIgnoreCase("PUT")
                || request.url() == null
                || request.url().isBlank()
                || request.headers() == null) {
            throw malformedResponse();
        }
        return request;
    }

    private int uploadBytes(YouCamFileMetadataResponse.UploadRequest uploadRequest, byte[] bytes) {
        URI uploadUri;
        try {
            uploadUri = URI.create(uploadRequest.url());
        } catch (IllegalArgumentException exception) {
            throw malformedResponse();
        }
        if (!uploadUri.isAbsolute()
                || uploadUri.getHost() == null
                || uploadUri.getScheme() == null
                || !(uploadUri.getScheme().equalsIgnoreCase("http")
                        || uploadUri.getScheme().equalsIgnoreCase("https"))) {
            throw malformedResponse();
        }

        try {
            ResponseEntity<Void> response = signedUploadClient.put()
                    .uri(uploadUri)
                    .headers(headers -> applyHeaders(headers, uploadRequest.headers()))
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
            return response.getStatusCode().value();
        } catch (RestClientResponseException exception) {
            LOGGER.warn("YouCam signed file upload failed: httpStatus={}", exception.getStatusCode().value());
            throw exceptionMapper.fromResponse(exception);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "YouCam signed file upload failed before receiving an HTTP response: transportError={}",
                    exception.getClass().getSimpleName());
            throw exceptionMapper.fromTransport(exception);
        } catch (IllegalArgumentException exception) {
            // Header validation errors can include signed metadata; do not let them reach generic logging.
            throw malformedResponse();
        }
    }

    private void applyHeaders(org.springframework.http.HttpHeaders requestHeaders, Map<String, String> headers) {
        headers.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null) {
                throw malformedResponse();
            }
            requestHeaders.set(name, value);
        });
    }

    private ProviderException malformedResponse() {
        return new ProviderException(
                org.springframework.http.HttpStatus.BAD_GATEWAY,
                "PROVIDER_MALFORMED_RESPONSE",
                "The skin analysis provider returned an unexpected response.");
    }
}
