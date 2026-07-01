package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
        new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE
        )
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName().toLowerCase(Locale.US);
        
        String ext = null;
        if (name.endsWith(".png")) {
            ext = "png";
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            ext = "jpg";
        } else if (name.endsWith(".gif")) {
            ext = "gif";
        } else if (name.endsWith(".webp")) {
            ext = "webp";
        }

        if (ext == null) {
            return;
        }

        byte[] header = new byte[12];
        int read = 0;
        try (InputStream is = new FileInputStream(file)) {
            read = is.read(header);
        } catch (IOException e) {
            return;
        }

        if (read < 4) {
            return;
        }

        String detected = detectFormat(header, read);
        if (detected != null && !detected.equals(ext)) {
            if (ext.equals("jpg") && detected.equals("jpg")) {
                return;
            }
            String message = String.format(
                "Misleading file extension; name ends with .%s but the file is a %s file",
                ext, detected.toUpperCase(Locale.US)
            );
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static String detectFormat(byte[] header, int read) {
        if (read >= 8 &&
            (header[0] & 0xFF) == 0x89 &&
            (header[1] & 0xFF) == 0x50 &&
            (header[2] & 0xFF) == 0x4E &&
            (header[3] & 0xFF) == 0x47 &&
            (header[4] & 0xFF) == 0x0D &&
            (header[5] & 0xFF) == 0x0A &&
            (header[6] & 0xFF) == 0x1A &&
            (header[7] & 0xFF) == 0x0A) {
            return "png";
        }
        if (read >= 3 &&
            (header[0] & 0xFF) == 0xFF &&
            (header[1] & 0xFF) == 0xD8 &&
            (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (read >= 6 &&
            (header[0] & 0xFF) == 'G' &&
            (header[1] & 0xFF) == 'I' &&
            (header[2] & 0xFF) == 'F' &&
            (header[3] & 0xFF) == '8' &&
            ((header[4] & 0xFF) == '7' || (header[4] & 0xFF) == '9') &&
            (header[5] & 0xFF) == 'a') {
            return "gif";
        }
        if (read >= 12 &&
            (header[0] & 0xFF) == 'R' &&
            (header[1] & 0xFF) == 'I' &&
            (header[2] & 0xFF) == 'F' &&
            (header[3] & 0xFF) == 'F' &&
            (header[8] & 0xFF) == 'W' &&
            (header[9] & 0xFF) == 'E' &&
            (header[10] & 0xFF) == 'B' &&
            (header[11] & 0xFF) == 'P') {
            return "webp";
        }
        
        for (int i = 0; i < read; i++) {
            byte b = header[i];
            if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                continue;
            }
            if (b == '<') {
                return "xml";
            }
            break;
        }
        return null;
    }
}