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

    public IconDetector() {
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return;
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase();

        // Only check image file extensions
        if (!extension.equals("png") && !extension.equals("jpg") && !extension.equals("jpeg")
                && !extension.equals("gif") && !extension.equals("webp")
                && !extension.equals("bmp")) {
            return;
        }

        String detectedFormat = detectImageFormat(file);
        if (detectedFormat == null) {
            return;
        }

        boolean matches = formatMatchesExtension(detectedFormat, extension);
        if (!matches) {
            String message = String.format(
                    "Misleading file extension: the file `%1$s` has the extension `.%2$s` " +
                    "but the file format is `%3$s`",
                    fileName, extension, detectedFormat);
            context.report(ICON_EXTENSION, context.getLocation(file), message);
        }
    }

    private static boolean formatMatchesExtension(String format, String extension) {
        switch (format) {
            case "PNG":
                return extension.equals("png");
            case "JPEG":
                return extension.equals("jpg") || extension.equals("jpeg");
            case "GIF":
                return extension.equals("gif");
            case "WEBP":
                return extension.equals("webp");
            case "BMP":
                return extension.equals("bmp");
            default:
                return true;
        }
    }

    private static String detectImageFormat(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int bytesRead = 0;
            int read;
            while (bytesRead < header.length) {
                read = is.read(header, bytesRead, header.length - bytesRead);
                if (read == -1) {
                    break;
                }
                bytesRead += read;
            }

            if (bytesRead < 4) {
                return null;
            }

            // PNG: starts with 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
            if (bytesRead >= 8
                    && (header[0] & 0xFF) == 0x89
                    && (header[1] & 0xFF) == 0x50
                    && (header[2] & 0xFF) == 0x4E
                    && (header[3] & 0xFF) == 0x47
                    && (header[4] & 0xFF) == 0x0D
                    && (header[5] & 0xFF) == 0x0A
                    && (header[6] & 0xFF) == 0x1A
                    && (header[7] & 0xFF) == 0x0A) {
                return "PNG";
            }

            // JPEG: starts with 0xFF 0xD8 0xFF
            if ((header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                return "JPEG";
            }

            // GIF: starts with "GIF87a" or "GIF89a"
            if ((header[0] & 0xFF) == 'G'
                    && (header[1] & 0xFF) == 'I'
                    && (header[2] & 0xFF) == 'F'
                    && (header[3] & 0xFF) == '8'
                    && ((header[4] & 0xFF) == '7' || (header[4] & 0xFF) == '9')
                    && (header[5] & 0xFF) == 'a') {
                return "GIF";
            }

            // WEBP: starts with "RIFF" followed by 4 bytes then "WEBP"
            if (bytesRead >= 12
                    && (header[0] & 0xFF) == 'R'
                    && (header[1] & 0xFF) == 'I'
                    && (header[2] & 0xFF) == 'F'
                    && (header[3] & 0xFF) == 'F'
                    && (header[8] & 0xFF) == 'W'
                    && (header[9] & 0xFF) == 'E'
                    && (header[10] & 0xFF) == 'B'
                    && (header[11] & 0xFF) == 'P') {
                return "WEBP";
            }

            // BMP: starts with "BM"
            if ((header[0] & 0xFF) == 'B'
                    && (header[1] & 0xFF) == 'M') {
                return "BMP";
            }

            return null;
        } catch (IOException e) {
            return null;
        }
    }
}