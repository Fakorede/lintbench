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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.jetbrains.annotations.NonNull;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is " +
            "really in the PNG format and not for example a GIF file named `.png`).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.BINARY_RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);

        if (!ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg") &&
                !ext.equals("gif") && !ext.equals("webp") && !ext.equals("xml")) {
            return;
        }

        byte[] header = new byte[16];
        try (InputStream is = new FileInputStream(file)) {
            int read = is.read(header);
            if (read < 3) {
                return;
            }
            if (read < 16) {
                byte[] temp = new byte[read];
                System.arraycopy(header, 0, temp, 0, read);
                header = temp;
            }
        } catch (IOException e) {
            return;
        }

        String actual = getActualFormat(header);
        if (actual == null) {
            return;
        }

        boolean match = false;
        if (ext.equals("png") && actual.equals("png")) {
            match = true;
        } else if ((ext.equals("jpg") || ext.equals("jpeg")) && actual.equals("jpg")) {
            match = true;
        } else if (ext.equals("gif") && actual.equals("gif")) {
            match = true;
        } else if (ext.equals("webp") && actual.equals("webp")) {
            match = true;
        } else if (ext.equals("xml") && actual.equals("xml")) {
            match = true;
        }

        if (!match) {
            String message = String.format(
                    "Misleading file extension; newly created `%s` file is actually a `%s` file",
                    ext, actual.toUpperCase(Locale.US));
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static String getActualFormat(byte[] header) {
        if (header.length >= 8 &&
                header[0] == (byte) 0x89 &&
                header[1] == (byte) 0x50 &&
                header[2] == (byte) 0x4E &&
                header[3] == (byte) 0x47 &&
                header[4] == (byte) 0x0D &&
                header[5] == (byte) 0x0A &&
                header[6] == (byte) 0x1A &&
                header[7] == (byte) 0x0A) {
            return "png";
        }
        if (header.length >= 3 &&
                header[0] == (byte) 0xFF &&
                header[1] == (byte) 0xD8 &&
                header[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (header.length >= 6 &&
                header[0] == (byte) 'G' &&
                header[1] == (byte) 'I' &&
                header[2] == (byte) 'F' &&
                header[3] == (byte) '8' &&
                (header[4] == (byte) '7' || header[4] == (byte) '9') &&
                header[5] == (byte) 'a') {
            return "gif";
        }
        if (header.length >= 12 &&
                header[0] == (byte) 'R' &&
                header[1] == (byte) 'I' &&
                header[2] == (byte) 'F' &&
                header[3] == (byte) 'F' &&
                header[8] == (byte) 'W' &&
                header[9] == (byte) 'E' &&
                header[10] == (byte) 'B' &&
                header[11] == (byte) 'P') {
            return "webp";
        }
        for (int i = 0; i < header.length; i++) {
            byte b = header[i];
            if (b == '<') {
                return "xml";
            } else if (b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                continue;
            } else if (b == (byte) 0xEF && (i + 2) < header.length &&
                    header[i + 1] == (byte) 0xBB && header[i + 2] == (byte) 0xBF) {
                i += 2;
                continue;
            }
            break;
        }
        return null;
    }
}