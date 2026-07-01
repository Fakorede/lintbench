package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.*;
import com.android.utils.XmlUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class IconDetector extends ResourceXmlDetector implements BinaryResourceScanner {

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
                    Scope.BINARY_RESOURCE_FILE_SCOPE
            )
    );

    public IconDetector() {
    }

    @Override
    public Collection<Issue> getIssues() {
        return Collections.singletonList(ICON_EXTENSION);
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) {
            return;
        }

        String extension = name.substring(dotIndex + 1).toLowerCase();

        // Only check known image extensions
        if (!extension.equals("png") && !extension.equals("jpg") &&
                !extension.equals("jpeg") && !extension.equals("gif") &&
                !extension.equals("webp") && !extension.equals("bmp")) {
            return;
        }

        String detectedFormat = detectImageFormat(file);
        if (detectedFormat == null) {
            return;
        }

        detectedFormat = detectedFormat.toLowerCase();

        boolean matches = formatMatchesExtension(detectedFormat, extension);
        if (!matches) {
            String expectedExtension = getExpectedExtension(detectedFormat);
            context.report(
                    ICON_EXTENSION,
                    Location.create(file),
                    String.format(
                            "Misleading file extension; named `.%1$s` but the file format is `%2$s`",
                            extension,
                            detectedFormat.toUpperCase()
                    )
            );
        }
    }

    private static boolean formatMatchesExtension(String format, String extension) {
        switch (extension) {
            case "png":
                return format.equals("png");
            case "jpg":
            case "jpeg":
                return format.equals("jpeg") || format.equals("jpg");
            case "gif":
                return format.equals("gif");
            case "webp":
                return format.equals("webp");
            case "bmp":
                return format.equals("bmp");
            default:
                return false;
        }
    }

    private static String getExpectedExtension(String format) {
        switch (format) {
            case "jpeg":
                return "jpg";
            default:
                return format;
        }
    }

    private static String detectImageFormat(File file) {
        try {
            byte[] header = new byte[16];
            try (FileInputStream fis = new FileInputStream(file)) {
                int read = fis.read(header);
                if (read < 4) {
                    return null;
                }
            }

            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (header[0] == (byte) 0x89 &&
                    header[1] == (byte) 0x50 &&
                    header[2] == (byte) 0x4E &&
                    header[3] == (byte) 0x47) {
                return "png";
            }

            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF &&
                    header[1] == (byte) 0xD8 &&
                    header[2] == (byte) 0xFF) {
                return "jpeg";
            }

            // GIF: GIF87a or GIF89a
            if (header[0] == (byte) 'G' &&
                    header[1] == (byte) 'I' &&
                    header[2] == (byte) 'F' &&
                    header[3] == (byte) '8') {
                return "gif";
            }

            // WebP: RIFF????WEBP
            if (header[0] == (byte) 'R' &&
                    header[1] == (byte) 'I' &&
                    header[2] == (byte) 'F' &&
                    header[3] == (byte) 'F') {
                // Need to check bytes 8-11 for "WEBP"
                byte[] webpHeader = new byte[12];
                try (FileInputStream fis2 = new FileInputStream(file)) {
                    int read = fis2.read(webpHeader);
                    if (read >= 12 &&
                            webpHeader[8] == (byte) 'W' &&
                            webpHeader[9] == (byte) 'E' &&
                            webpHeader[10] == (byte) 'B' &&
                            webpHeader[11] == (byte) 'P') {
                        return "webp";
                    }
                }
            }

            // BMP: BM
            if (header[0] == (byte) 'B' && header[1] == (byte) 'M') {
                return "bmp";
            }

            return null;
        } catch (IOException e) {
            return null;
        }
    }
}