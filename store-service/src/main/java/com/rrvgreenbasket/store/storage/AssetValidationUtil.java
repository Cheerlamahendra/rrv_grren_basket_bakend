package com.rrvgreenbasket.store.storage;

import com.rrvgreenbasket.store.enums.AssetType;
import com.rrvgreenbasket.store.exception.FileUploadException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public final class AssetValidationUtil {

    public static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final Set<String> ALLOWED_LOGO_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp", ".svg");
    private static final Set<String> ALLOWED_FAVICON_EXTENSIONS = Set.of(".ico", ".png");

    private AssetValidationUtil() {
    }

    public static void validateAsset(MultipartFile file, AssetType assetType) {
        if (file == null || file.isEmpty()) {
            throw new FileUploadException("Uploaded file cannot be null or empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new FileUploadException(String.format("File size (%d bytes) exceeds the maximum allowed limit of %d bytes (2 MB)",
                    file.getSize(), MAX_FILE_SIZE_BYTES));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new FileUploadException("Uploaded file must have a valid extension");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();

        if (assetType == AssetType.LOGO) {
            validateLogo(file, extension, contentType);
        } else if (assetType == AssetType.FAVICON) {
            validateFavicon(file, extension, contentType);
        }
    }

    private static void validateLogo(MultipartFile file, String extension, String contentType) {
        if (!ALLOWED_LOGO_EXTENSIONS.contains(extension)) {
            throw new FileUploadException("Invalid logo file extension. Allowed formats: PNG, JPG, JPEG, WEBP, SVG");
        }

        byte[] header = readHeader(file, 512);

        if (".svg".equals(extension)) {
            validateSvgContent(file);
        } else if (".png".equals(extension)) {
            if (!isPng(header)) {
                throw new FileUploadException("File content does not match valid PNG format");
            }
        } else if (".jpg".equals(extension) || ".jpeg".equals(extension)) {
            if (!isJpeg(header)) {
                throw new FileUploadException("File content does not match valid JPEG format");
            }
        } else if (".webp".equals(extension)) {
            if (!isWebp(header)) {
                throw new FileUploadException("File content does not match valid WEBP format");
            }
        }
    }

    private static void validateFavicon(MultipartFile file, String extension, String contentType) {
        if (!ALLOWED_FAVICON_EXTENSIONS.contains(extension)) {
            throw new FileUploadException("Invalid favicon file extension. Allowed formats: ICO, PNG");
        }

        byte[] header = readHeader(file, 64);

        if (".ico".equals(extension)) {
            if (!isIco(header)) {
                throw new FileUploadException("File content does not match valid ICO format");
            }
        } else if (".png".equals(extension)) {
            if (!isPng(header)) {
                throw new FileUploadException("File content does not match valid PNG format");
            }
        }
    }

    private static void validateSvgContent(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

            // Must contain an <svg tag
            if (!content.contains("<svg")) {
                throw new FileUploadException("File does not appear to be a valid SVG document");
            }

            // Reject dangerous script, external entities, or event handlers
            if (content.contains("<script") || content.contains("javascript:")
                    || content.contains("onerror=") || content.contains("onload=")
                    || content.contains("onclick=") || content.contains("<!entity")
                    || content.contains("<!doctype")) {
                throw new FileUploadException("SVG contains forbidden script or entity directives and was rejected for security");
            }
        } catch (IOException e) {
            throw new FileUploadException("Failed to read SVG content: " + e.getMessage(), e);
        }
    }

    private static byte[] readHeader(MultipartFile file, int size) {
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[size];
            int read = is.read(buffer);
            if (read < 4) {
                throw new FileUploadException("File is corrupted or too small to be a valid image");
            }
            return buffer;
        } catch (IOException e) {
            throw new FileUploadException("Failed to inspect file header: " + e.getMessage(), e);
        }
    }

    private static boolean isPng(byte[] header) {
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        return header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50 // 'P'
                && header[2] == 0x4E // 'N'
                && header[3] == 0x47 // 'G'
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A;
    }

    private static boolean isJpeg(byte[] header) {
        // JPEG magic bytes: FF D8 FF
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] header) {
        // RIFF....WEBP
        return header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    private static boolean isIco(byte[] header) {
        // ICO magic bytes: 00 00 01 00
        return header.length >= 4
                && header[0] == 0x00
                && header[1] == 0x00
                && header[2] == 0x01
                && header[3] == 0x00;
    }
}
