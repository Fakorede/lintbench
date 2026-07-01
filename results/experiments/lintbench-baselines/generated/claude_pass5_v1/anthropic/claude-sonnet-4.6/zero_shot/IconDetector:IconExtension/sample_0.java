package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;

public class IconDetector extends ResourceDetector implements BinaryResourceScanner {

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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return;
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase();

        // Only check image file extensions
        if (!extension.equals("png") && !extension.equals("jpg") &&
                !extension.equals("jpeg") && !extension.equals("gif") &&
                !extension.equals("webp") && !extension.equals("bmp")) {
            return;
        }

        byte[] header = new byte[12];
        int bytesRead = 0;
        try {
            FileInputStream fis = new FileInputStream(file);
            try {
                bytesRead = fis.read(header);
            } finally {
                fis.close();
            }
        } catch (IOException e) {
            return;
        }

        if (bytesRead < 4) {
            return;
        }

        String detectedFormat = detectFormat(header, bytesRead);
        if (detectedFormat == null) {
            return;
        }

        boolean matches = formatMatchesExtension(detectedFormat, extension);
        if (!matches) {
            context.report(
                    ICON_EXTENSION,
                    context.getLocation(file),
                    String.format(
                            "Misleading file extension: the file `%1$s` has the extension `.%2$s` " +
                            "but the file format is `%3$s`",
                            fileName, extension, detectedFormat.toLowerCase()
                    )
            );
        }
    }

    private static String detectFormat(byte[] header, int length) {
        // PNG: starts with 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
        if (length >= 4 &&
                (header[0] & 0xFF) == 0x89 &&
                (header[1] & 0xFF) == 0x50 &&
                (header[2] & 0xFF) == 0x4E &&
                (header[3] & 0xFF) == 0x47) {
            return "PNG";
        }

        // JPEG: starts with 0xFF 0xD8 0xFF
        if (length >= 3 &&
                (header[0] & 0xFF) == 0xFF &&
                (header[1] & 0xFF) == 0xD8 &&
                (header[2] & 0xFF) == 0xFF) {
            return "JPEG";
        }

        // GIF: starts with "GIF87a" or "GIF89a"
        if (length >= 6 &&
                header[0] == 'G' &&
                header[1] == 'I' &&
                header[2] == 'F' &&
                header[3] == '8' &&
                (header[4] == '7' || header[4] == '9') &&
                header[5] == 'a') {
            return "GIF";
        }

        // WebP: starts with "RIFF" followed by 4 bytes then "WEBP"
        if (length >= 12 &&
                header[0] == 'R' &&
                header[1] == 'I' &&
                header[2] == 'F' &&
                header[3] == 'F' &&
                header[8] == 'W' &&
                header[9] == 'E' &&
                header[10] == 'B' &&
                header[11] == 'P') {
            return "WEBP";
        }

        // BMP: starts with "BM"
        if (length >= 2 &&
                header[0] == 'B' &&
                header[1] == 'M') {
            return "BMP";
        }

        return null;
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
}