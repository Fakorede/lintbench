package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public class IconDetector extends Detector {
    public static final Issue ISSUE = Issue.create(
        "IconExtension",
        "Icon format does not match the file extension",
        "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
        Category.CORRECTNESS, 6, Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE)
    );

    @Override
    public Scope getApplicableFiles() {
        return Scope.RESOURCE_FILE;
    }

    @Override
    public void visitFile(Context context) {
        File file = context.file;
        if (file == null) return;

        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) return;

        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        if (!isImageExtension(ext)) return;

        File parent = file.getParentFile();
        if (parent == null) return;
        String folder = parent.getName();
        if (!folder.startsWith("drawable") && !folder.startsWith("mipmap")) return;

        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 2) return;

            String actualFormat = detectFormat(header, read);
            if (actualFormat == null) return;

            if (!matchesExtension(actualFormat, ext)) {
                String message = String.format(
                    "The file is actually a %s file but has the extension .%s",
                    actualFormat.toUpperCase(Locale.US), ext);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isImageExtension(String ext) {
        return ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg") ||
               ext.equals("gif") || ext.equals("webp") || ext.equals("bmp");
    }

    private static String detectFormat(byte[] header, int length) {
        if (length >= 8 && header[0] == (byte)0x89 && header[1] == (byte)0x50 && header[2] == (byte)0x4E && header[3] == (byte)0x47) {
            return "png";
        }
        if (length >= 3 && header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF) {
            return "jpg";
        }
        if (length >= 4 && header[0] == (byte)0x47 && header[1] == (byte)0x49 && header[2] == (byte)0x46 && header[3] == (byte)0x38) {
            return "gif";
        }
        if (length >= 12 && header[0] == (byte)0x52 && header[1] == (byte)0x49 && header[2] == (byte)0x46 && header[3] == (byte)0x46 &&
            header[8] == (byte)0x57 && header[9] == (byte)0x45 && header[10] == (byte)0x42 && header[11] == (byte)0x50) {
            return "webp";
        }
        if (length >= 2 && header[0] == (byte)0x42 && header[1] == (byte)0x4D) {
            return "bmp";
        }
        return null;
    }

    private static boolean matchesExtension(String format, String ext) {
        if (format.equals("jpg")) {
            return ext.equals("jpg") || ext.equals("jpeg");
        }
        return format.equals(ext);
    }
}