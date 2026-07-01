package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.BinaryResourceContext;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

public class IconDetector extends Detector implements BinaryResourceScanner {

    private static final int HEADER_LENGTH = 12;

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "The file extension of an icon should match its actual image format. "
                    + "For example, a `.png` file must really be a PNG image, not a GIF or WebP "
                    + "file that has simply been renamed. Mismatched extensions can cause build "
                    + "failures or unexpected rendering on devices.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public void checkBinaryResourceResource(@NonNull BinaryResourceContext context) {
        File file = context.getFile();

        String folderName = file.getParentFile().getName();
        if (!folderName.startsWith("drawable") && !folderName.startsWith("mipmap")) {
            return;
        }

        String fileName = file.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return;
        }

        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!isKnownImageExtension(extension)) {
            return;
        }

        byte[] header = new byte[HEADER_LENGTH];
        try (FileInputStream input = new FileInputStream(file)) {
            int read = input.read(header);
            if (read < 8) {
                return;
            }

            String actualFormat = detectImageFormat(header);
            if (actualFormat == null) {
                return;
            }

            if (!matchesExtension(actualFormat, extension)) {
                String message = String.format(
                        "The icon file extension `.%s` does not match its actual image format (%s)",
                        extension, actualFormat);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
            // Ignore files that cannot be read.
        }
    }

    private static boolean isKnownImageExtension(@NonNull String extension) {
        return extension.equals("png")
                || extension.equals("gif")
                || extension.equals("webp")
                || extension.equals("jpg")
                || extension.equals("jpeg");
    }

    private static boolean matchesExtension(@NonNull String format, @NonNull String extension) {
        if (format.equals("jpeg")) {
            return extension.equals("jpg") || extension.equals("jpeg");
        }
        return format.equals(extension);
    }

    @Nullable
    private static String detectImageFormat(@NonNull byte[] header) {
        if (header[0] == (byte) 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47) {
            return "png";
        }
        if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
            return "gif";
        }
        if (header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8
                && header[2] == (byte) 0xFF) {
            return "jpeg";
        }
        if (header.length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            return "webp";
        }
        return null;
    }
}