package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.CORRECTNESS,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE));

    @Override
    public void visitBinaryResource(ResourceContext context) {
        File file = context.getFile();
        if (file == null || !file.isFile()) {
            return;
        }

        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return;
        }

        String ext = name.substring(dotIndex + 1).toLowerCase(Locale.US);
        if (!isImageExtension(ext)) {
            return;
        }

        byte[] header = readHeader(file, 12);
        if (header == null || header.length < 4) {
            return;
        }

        String actualFormat = detectFormat(header);
        if (actualFormat == null) {
            return;
        }

        if (!matchesExtension(actualFormat, ext)) {
            String message = String.format(Locale.US,
                    "The file extension `%s` does not match the actual file format `%s`",
                    ext, actualFormat.toUpperCase(Locale.US));
            Location location = Location.create(file);
            context.report(ISSUE, location, message);
        }
    }

    private static boolean isImageExtension(String ext) {
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") ||
               ext.equals("gif") || ext.equals("webp") || ext.equals("bmp");
    }

    private static String detectFormat(byte[] header) {
        if (header.length >= 4 &&
                header[0] == (byte) 0x89 && header[1] == (byte) 0x50 &&
                header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
            return "png";
        }
        if (header.length >= 3 &&
                header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 &&
                header[2] == (byte) 0xFF) {
            return "jpeg";
        }
        if (header.length >= 4 &&
                header[0] == (byte) 0x47 && header[1] == (byte) 0x49 &&
                header[2] == (byte) 0x46 && header[3] == (byte) 0x38) {
            return "gif";
        }
        if (header.length >= 12 &&
                header[0] == (byte) 0x52 && header[1] == (byte) 0x49 &&
                header[2] == (byte) 0x46 && header[3] == (byte) 0x46 &&
                header[8] == (byte) 0x57 && header[9] == (byte) 0x45 &&
                header[10] == (byte) 0x42 && header[11] == (byte) 0x50) {
            return "webp";
        }
        if (header.length >= 2 &&
                header[0] == (byte) 0x42 && header[1] == (byte) 0x4D) {
            return "bmp";
        }
        return null;
    }

    private static boolean matchesExtension(String format, String ext) {
        if (format.equals(ext)) {
            return true;
        }
        if (format.equals("jpeg") && (ext.equals("jpg") || ext.equals("jpeg"))) {
            return true;
        }
        return false;
    }

    private static byte[] readHeader(File file, int length) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[length];
            int read = fis.read(buffer);
            if (read <= 0) {
                return new byte[0];
            }
            return Arrays.copyOf(buffer, read);
        } catch (IOException e) {
            return null;
        }
    }
}