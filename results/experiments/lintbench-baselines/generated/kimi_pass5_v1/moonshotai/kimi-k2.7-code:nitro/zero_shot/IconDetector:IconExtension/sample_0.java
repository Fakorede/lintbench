package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.io.InputStream;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.ResourceFolderDetector {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                    + "really in the PNG format and not, for example, a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int HEADER_SIZE = 64;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFile(@NonNull ResourceContext context) {
        File file = context.getFile();
        if (file.isDirectory()) {
            return;
        }

        String extension = getExtension(file.getName());
        if (extension == null) {
            return;
        }

        String actualFormat = detectActualFormat(file);
        if (actualFormat == null) {
            return;
        }

        String expectedFormat = normalizeExtension(extension);
        if (!actualFormat.equals(expectedFormat)) {
            String message = String.format(
                    "The file extension does not match the actual icon format: "
                            + "file is `%1$s` but extension is `.%2$s`",
                    actualFormat, extension);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static String getExtension(String name) {
        int index = name.lastIndexOf('.');
        if (index <= 0 || index == name.length() - 1) {
            return null;
        }
        return name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeExtension(String extension) {
        if ("jpeg".equals(extension)) {
            return "jpg";
        }
        return extension;
    }

    private static String detectActualFormat(File file) {
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = new byte[HEADER_SIZE];
            int read = is.read(header);
            if (read < 4) {
                return null;
            }

            if (isPng(header)) {
                return "png";
            } else if (isGif(header)) {
                return "gif";
            } else if (isJpeg(header)) {
                return "jpg";
            } else if (isWebp(header, read)) {
                return "webp";
            } else if (isBmp(header)) {
                return "bmp";
            } else if (isXml(header, read)) {
                return "xml";
            }
        } catch (IOException e) {
            // Ignore files we cannot read.
        }
        return null;
    }

    private static boolean isPng(byte[] header) {
        return header.length >= 8
                && header[0] == (byte) 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A;
    }

    private static boolean isGif(byte[] header) {
        return header.length >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a';
    }

    private static boolean isJpeg(byte[] header) {
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    private static boolean isWebp(byte[] header, int length) {
        return length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P';
    }

    private static boolean isBmp(byte[] header) {
        return header.length >= 2
                && header[0] == 'B'
                && header[1] == 'M';
    }

    private static boolean isXml(byte[] header, int length) {
        int i = 0;
        while (i < length) {
            byte b = header[i];
            if (b == ' ' || b == '\t' || b == '\n' || b == '\r') {
                i++;
            } else {
                break;
            }
        }
        return i < length && header[i] == '<';
    }
}