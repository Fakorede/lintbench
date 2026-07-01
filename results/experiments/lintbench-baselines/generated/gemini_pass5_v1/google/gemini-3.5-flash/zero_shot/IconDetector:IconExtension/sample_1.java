package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        String name = file.getName().toLowerCase(Locale.US);

        String expected = null;
        if (name.endsWith(".png")) {
            expected = "png";
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            expected = "jpeg";
        } else if (name.endsWith(".gif")) {
            expected = "gif";
        } else if (name.endsWith(".webp")) {
            expected = "webp";
        }

        if (expected == null) {
            return;
        }

        byte[] header = new byte[12];
        int bytesRead = 0;
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            bytesRead = bis.read(header);
        } catch (IOException e) {
            return;
        }

        if (bytesRead < 3) {
            return;
        }

        String actual = null;
        if (isPng(header, bytesRead)) {
            actual = "png";
        } else if (isJpeg(header, bytesRead)) {
            actual = "jpeg";
        } else if (isGif(header, bytesRead)) {
            actual = "gif";
        } else if (isWebp(header, bytesRead)) {
            actual = "webp";
        }

        if (actual != null && !actual.equals(expected)) {
            String message = String.format(
                    "Misleading file extension; This file is formatted as %s but has extension .%s",
                    actual.toUpperCase(Locale.US),
                    expected
            );
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static boolean isPng(byte[] bytes, int length) {
        return length >= 8 &&
               (bytes[0] & 0xFF) == 0x89 &&
               (bytes[1] & 0xFF) == 0x50 &&
               (bytes[2] & 0xFF) == 0x4E &&
               (bytes[3] & 0xFF) == 0x47 &&
               (bytes[4] & 0xFF) == 0x0D &&
               (bytes[5] & 0xFF) == 0x0A &&
               (bytes[6] & 0xFF) == 0x1A &&
               (bytes[7] & 0xFF) == 0x0A;
    }

    private static boolean isGif(byte[] bytes, int length) {
        return length >= 6 &&
               (bytes[0] & 0xFF) == 'G' &&
               (bytes[1] & 0xFF) == 'I' &&
               (bytes[2] & 0xFF) == 'F' &&
               (bytes[3] & 0xFF) == '8' &&
               ((bytes[4] & 0xFF) == '7' || (bytes[4] & 0xFF) == '9') &&
               (bytes[5] & 0xFF) == 'a';
    }

    private static boolean isJpeg(byte[] bytes, int length) {
        return length >= 3 &&
               (bytes[0] & 0xFF) == 0xFF &&
               (bytes[1] & 0xFF) == 0xD8 &&
               (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] bytes, int length) {
        return length >= 12 &&
               (bytes[0] & 0xFF) == 'R' &&
               (bytes[1] & 0xFF) == 'I' &&
               (bytes[2] & 0xFF) == 'F' &&
               (bytes[3] & 0xFF) == 'F' &&
               (bytes[8] & 0xFF) == 'W' &&
               (bytes[9] & 0xFF) == 'E' &&
               (bytes[10] & 0xFF) == 'B' &&
               (bytes[11] & 0xFF) == 'P';
    }
}