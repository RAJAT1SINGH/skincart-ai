package com.skincart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkinCartApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void exposesActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void allowsTheConfiguredLocalFrontendOriginForApiRequests() throws Exception {
        mockMvc.perform(options("/api/v1/skin-analysis")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void completesAnalysisInMockModeWithoutProviderCredentials() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "selfie.png", "image/png", pngImage());

        var result = mockMvc.perform(multipart("/api/v1/skin-analysis").file(image))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        String location = result.getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.concerns.length()").value(4))
                .andExpect(jsonPath("$.concerns[0].uiScore").exists())
                .andExpect(jsonPath("$.concerns[0].rawScore").exists())
                .andExpect(jsonPath("$.providerName").doesNotExist())
                .andExpect(jsonPath("$.providerTaskId").doesNotExist());
    }

    @Test
    void recommendsSeededProductsForACompletedMockAnalysis() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "selfie.png", "image/png", pngImage());
        var result = mockMvc.perform(multipart("/api/v1/skin-analysis").file(image))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");

        mockMvc.perform(get(location + "/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").exists())
                .andExpect(jsonPath("$.recommendations.length()")
                        .value(greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.recommendations.length()")
                        .value(lessThanOrEqualTo(5)))
                .andExpect(jsonPath("$.recommendations[0].productName").exists())
                .andExpect(jsonPath("$.recommendations[0].matchedConcerns.length()")
                        .value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recommendations[0].reason").exists());

        mockMvc.perform(get(location + "/routine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").exists())
                .andExpect(jsonPath("$.morning.length()").value(4))
                .andExpect(jsonPath("$.morning[0].step").value("CLEANSER"))
                .andExpect(jsonPath("$.morning[0].productName").value("Demo Clear Path Gel Cleanser"))
                .andExpect(jsonPath("$.morning[1].step").value("TREATMENT"))
                .andExpect(jsonPath("$.morning[1].productName").value("Demo Balance Grid Serum"))
                .andExpect(jsonPath("$.morning[2].step").value("MOISTURIZER"))
                .andExpect(jsonPath("$.morning[2].productName").value("Demo Soft Focus Daily Moisturizer"))
                .andExpect(jsonPath("$.morning[3].step").value("SUNSCREEN"))
                .andExpect(jsonPath("$.morning[3].productName").value("Demo Daylight Guard SPF 30"))
                .andExpect(jsonPath("$.evening.length()").value(3))
                .andExpect(jsonPath("$.evening[0].step").value("CLEANSER"))
                .andExpect(jsonPath("$.evening[1].step").value("TREATMENT"))
                .andExpect(jsonPath("$.evening[2].step").value("MOISTURIZER"))
                .andExpect(jsonPath("$.morning[0].reason").exists());
    }

    private byte[] pngImage() {
        try {
            BufferedImage image = new BufferedImage(480, 640, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
