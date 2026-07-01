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
import java.io.InputStream;
import java.util.EnumSet;

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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)
            )
    );

    public IconDetector() {
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

        // Only check image file extensions
        if (!extension.equals("png") && !extension.equals("jpg") &&
                !extension.equals("jpeg") && !extension.equals("gif") &&
                !extension.equals("webp") && !extension.equals("bmp")) {
            return;
        }

        byte[] header = readHeader(file, 12);
        if (header == null || header.length < 4) {
            return;
        }

        String detectedFormat = detectFormat(header);
        if (detectedFormat == null) {
            return;
        }

        boolean matches = formatMatchesExtension(detectedFormat, extension);
        if (!matches) {
            context.report(
                    ICON_EXTENSION,
                    context.getLocation(file),
                    String.format(
                            "Misleading file extension; named `.%1$s` but the file format is `%2$s`",
                            extension,
                            detectedFormat
                    )
            );
        }
    }

    private static byte[] readHeader(File file, int bytes) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buffer = new byte[bytes];
            int read = 0;
            while (read < bytes) {
                int count = is.read(buffer, read, bytes - read);
                if (count == -1) {
                    break;
                }
                read += count;
            }
            if (read == 0) {
                return null;
            }
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

    private static String detectFormat(byte[] header) {
        if (header.length >= 4) {
            // PNG: 89 50 4E 47
            if ((header[0] & 0xFF) == 0x89 &&
                    (header[1] & 0xFF) == 0x50 &&
                    (header[2] & 0xFF) == 0x4E &&
                    (header[3] & 0xFF) == 0x47) {
                return "PNG";
            }
        }

        if (header.length >= 3) {
            // GIF: 47 49 46
            if ((header[0] & 0xFF) == 0x47 &&
                    (header[1] & 0xFF) == 0x49 &&
                    (header[2] & 0xFF) == 0x46) {
                return "GIF";
            }
        }

        if (header.length >= 2) {
            // JPEG: FF D8
            if ((header[0] & 0xFF) == 0xFF &&
                    (header[1] & 0xFF) == 0xD8) {
                return "JPEG";
            }

            // BMP: 42 4D
            if ((header[0] & 0xFF) == 0x42 &&
                    (header[1] & 0xFF) == 0x4D) {
                return "BMP";
            }
        }

        if (header.length >= 12) {
            // WebP: 52 49 46 46 ?? ?? ?? ?? 57 45 42 50
            if ((header[0] & 0xFF) == 0x52 &&
                    (header[1] & 0xFF) == 0x49 &&
                    (header[2] & 0xFF) == 0x46 &&
                    (header[3] & 0xFF) == 0x46 &&
                    (header[8] & 0xFF) == 0x57 &&
                    (header[9] & 0xFF) == 0x45 &&
                    (header[10] & 0xFF) == 0x42 &&
                    (header[11] & 0xFF) == 0x50) {
                return "WebP";
            }
        }

        return null;
    }

    private static boolean formatMatchesExtension(String format, String extension) {
        switch (format) {
            case "PNG":
                return extension.equals("png");
            case "GIF":
                return extension.equals("gif");
            case "JPEG":
                return extension.equals("jpg") || extension.equals("jpeg");
            case "BMP":
                return extension.equals("bmp");
            case "WebP":
                return extension.equals("webp");
            default:
                return true;
        }
    }

    @Override
    public void run(@NonNull Context context) {
        // Not used
    }
}