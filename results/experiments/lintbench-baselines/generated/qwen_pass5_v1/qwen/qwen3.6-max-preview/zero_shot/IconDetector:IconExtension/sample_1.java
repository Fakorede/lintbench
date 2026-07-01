package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.BinaryResourceContext;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NonNull;
import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.CORRECTNESS,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @NonNull
    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.RESOURCE_FILE_SCOPE;
    }

    @Override
    public void visitBinaryResource(@NonNull BinaryResourceContext context) {
        String fileName = context.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
            return;
        }
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        String expectedFormat = getExpectedFormat(ext);
        if (expectedFormat == null) {
            return;
        }

        byte[] contents = context.getContents();
        if (contents == null || contents.length < 4) {
            return;
        }

        String actualFormat = detectFormat(contents);
        if (actualFormat != null && !actualFormat.equals(expectedFormat)) {
            context.report(ISSUE, context.getLocation(),
                    "The file is named with a ." + ext + " extension, but it appears to be a " + actualFormat + " file");
        }
    }

    private static String getExpectedFormat(String ext) {
        switch (ext) {
            case "png": return "png";
            case "jpg":
            case "jpeg": return "jpeg";
            case "gif": return "gif";
            case "webp": return "webp";
            case "bmp": return "bmp";
            default: return null;
        }
    }

    private static String detectFormat(byte[] bytes) {
        if (bytes.length >= 8 &&
            (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G' &&
            bytes[4] == '\r' && bytes[5] == '\n' && bytes[6] == 0x1A && bytes[7] == '\n') {
            return "png";
        }
        if (bytes.length >= 3 &&
            (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (bytes.length >= 4 &&
            bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return "gif";
        }
        if (bytes.length >= 12 &&
            bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
            bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return "bmp";
        }
        return null;
    }
}