package com.skincart.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skincart.exception.ApplicationException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class ImageUploadValidatorTest {

    @Test
    void acceptsPngWithMatchingContentTypeSignatureAndMinimumDimensions() {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(1));
        MockMultipartFile image = new MockMultipartFile("image", "selfie.png", "image/png", png(480, 640));

        assertThatCode(() -> validator.validate(image)).doesNotThrowAnyException();
    }

    @Test
    void rejectsImageBelowDocumentedMinimumShortSide() {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(1));
        MockMultipartFile image = new MockMultipartFile("image", "selfie.png", "image/png", png(479, 640));

        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("The uploaded image must be at least 480 pixels on its shorter side.");
    }

    @Test
    void rejectsEmptyImage() {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(1));
        MockMultipartFile image = new MockMultipartFile("image", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> {
                    ApplicationException apiException = (ApplicationException) exception;
                    org.assertj.core.api.Assertions.assertThat(apiException.getCode()).isEqualTo("EMPTY_IMAGE");
                    org.assertj.core.api.Assertions.assertThat(apiException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void rejectsUnsupportedImageType() {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(1));
        MockMultipartFile image = new MockMultipartFile("image", "selfie.gif", "image/gif", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Only JPEG and PNG images are supported.");
    }

    @Test
    void rejectsDeclaredImageTypeWhenSignatureDoesNotMatch() {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(1));
        MockMultipartFile image = new MockMultipartFile("image", "selfie.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("The uploaded file content does not match its image type.");
    }

    @Test
    void rejectsImageLargerThanConfiguredLimit() {
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofBytes(4));
        MockMultipartFile image = new MockMultipartFile(
                "image", "selfie.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D});

        assertThatThrownBy(() -> validator.validate(image))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Uploaded image exceeds the maximum allowed size.");
    }

    private byte[] png(int width, int height) {
        try {
            BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
