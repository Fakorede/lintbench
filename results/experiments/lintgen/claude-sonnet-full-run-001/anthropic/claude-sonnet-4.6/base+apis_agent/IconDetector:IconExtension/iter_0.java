package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ICON_EXTENSION = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is " +
            "really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE)
            )
    );

    // Magic bytes for common image formats
    private static final byte[] PNG_MAGIC  = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF89_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    private static final byte[] JPEG_MAGIC  = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
    private static final byte[] WEBP_RIFF   = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50}; // "WEBP" at offset 8

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
            return;
        }
        String extension = name.substring(dotIndex + 1).toLowerCase();

        // Only check known image extensions
        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")
                && !extension.equals("gif") && !extension.equals("webp")) {
            return;
        }

        byte[] header = readHeader(file, 12);
        if (header == null || header.length < 3) {
            return;
        }

        String detectedFormat = detectFormat(header);
        if (detectedFormat == null) {
            return;
        }

        boolean matches;
        switch (extension) {
            case "png":
                matches = detectedFormat.equals("PNG");
                break;
            case "jpg":
            case "jpeg":
                matches = detectedFormat.equals("JPEG");
                break;
            case "gif":
                matches = detectedFormat.equals("GIF");
                break;
            case "webp":
                matches = detectedFormat.equals("WEBP");
                break;
            default:
                matches = true;
                break;
        }

        if (!matches) {
            String message = String.format(
                    "Misleading file extension: the file `%1$s` has the `.%2$s` extension " +
                    "but the file format is `%3$s`",
                    name, extension, detectedFormat);
            context.report(ICON_EXTENSION, context.getLocation(file), message);
        }
    }

    private static String detectFormat(byte[] header) {
        if (startsWith(header, PNG_MAGIC)) {
            return "PNG";
        }
        if (startsWith(header, JPEG_MAGIC)) {
            return "JPEG";
        }
        if (startsWith(header, GIF87_MAGIC) || startsWith(header, GIF89_MAGIC)) {
            return "GIF";
        }
        // WEBP: "RIFF" at offset 0, "WEBP" at offset 8
        if (header.length >= 12
                && startsWith(header, WEBP_RIFF)
                && header[8] == WEBP_MARKER[0]
                && header[9] == WEBP_MARKER[1]
                && header[10] == WEBP_MARKER[2]
                && header[11] == WEBP_MARKER[3]) {
            return "WEBP";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readHeader(File file, int bytes) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[bytes];
            int read = 0;
            while (read < bytes) {
                int n = is.read(buffer, read, bytes - read);
                if (n < 0) break;
                read += n;
            }
            if (read == 0) return null;
            if (read < bytes) {
                byte[] trimmed = new byte[read];
                System.arraycopy(buffer, 0, trimmed, 0, read);
                return trimmed;
            }
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }
}