package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "IconExtension",
        "Icon format does not match the file extension",
        "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.IMAGE_FILE)
    );

    @Override
    public void visitBinaryFile(@NotNull Context context) {
        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);

        if (!ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg") &&
            !ext.equals("gif") && !ext.equals("webp") && !ext.equals("bmp")) {
            return;
        }

        try (InputStream is = new FileInputStream(context.file)) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 2) {
                return;
            }

            String actual = getActualFormat(header, read);
            if (actual == null) {
                return;
            }

            String expected = ext;
            if (expected.equals("jpeg")) {
                expected = "jpg";
            }

            if (!expected.equals(actual)) {
                String message = String.format(
                    Locale.US,
                    "The file name suggests this is a %s file, but it appears to be a %s file",
                    ext.toUpperCase(Locale.US),
                    actual.toUpperCase(Locale.US)
                );
                context.report(ISSUE, Location.create(context.file), message);
            }
        } catch (IOException ignored) {
        }
    }

    private static String getActualFormat(byte[] header, int length) {
        if (length >= 8 && header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "png";
        }
        if (length >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (length >= 4 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return "gif";
        }
        if (length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
            header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        if (length >= 2 && header[0] == 'B' && header[1] == 'M') {
            return "bmp";
        }
        return null;
    }
}