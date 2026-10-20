package com.skincart.service;

import com.skincart.exception.InvalidImageException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageUploadValidator {

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final long maxFileSizeBytes;

    public ImageUploadValidator(@Value("${app.upload.max-file-size:10MB}") DataSize maxFileSize) {
        this.maxFileSizeBytes = maxFileSize.toBytes();
    }

    public void validate(MultipartFile image) {
        if (image == null) {
            throw invalidImage(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "An image file is required.");
        }
        if (image.isEmpty() || image.getSize() == 0) {
            throw invalidImage(HttpStatus.BAD_REQUEST, "EMPTY_IMAGE", "The uploaded image is empty.");
        }
        if (image.getSize() >= maxFileSizeBytes) {
            throw invalidImage(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "Uploaded image exceeds the maximum allowed size.");
        }

        String contentType = normalizedContentType(image.getContentType());
        if (!contentType.equals("image/jpeg") && !contentType.equals("image/jpg") && !contentType.equals("image/png")) {
            throw invalidImage(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_IMAGE_TYPE",
                    "Only JPEG and PNG images are supported.");
        }

        try (InputStream input = image.getInputStream()) {
            byte[] signature = input.readNBytes(PNG_SIGNATURE.length);
            boolean matches = contentType.equals("image/png")
                    ? startsWith(signature, PNG_SIGNATURE)
                    : startsWith(signature, JPEG_SIGNATURE);
            if (!matches) {
                throw invalidImage(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_IMAGE",
                        "The uploaded file content does not match its image type.");
            }
        } catch (IOException exception) {
            throw invalidImage(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The uploaded image could not be read.");
        }

        validateMinimumDimensions(image);
    }

    private String normalizedContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean startsWith(byte[] candidate, byte[] signature) {
        if (candidate.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (candidate[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private void validateMinimumDimensions(MultipartFile image) {
        try (InputStream input = image.getInputStream();
                ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw invalidImage(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The uploaded image could not be read.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The uploaded image could not be read.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (Math.min(width, height) < 480) {
                    throw invalidImage(
                            HttpStatus.BAD_REQUEST,
                            "IMAGE_BELOW_MINIMUM_SIZE",
                            "The uploaded image must be at least 480 pixels on its shorter side.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalidImage(HttpStatus.BAD_REQUEST, "INVALID_IMAGE", "The uploaded image could not be read.");
        }
    }

    private InvalidImageException invalidImage(HttpStatus status, String code, String message) {
        return new InvalidImageException(status, code, message);
    }
}
