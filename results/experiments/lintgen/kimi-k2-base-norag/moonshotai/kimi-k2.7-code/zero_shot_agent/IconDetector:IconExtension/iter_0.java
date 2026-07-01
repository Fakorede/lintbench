package com.android.tools.lint.checks;

import androidx.annotation.NonNull;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

public class IconDetector extends ResourceFolderDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.RESOURCE_FOLDER_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension "
                    + "(e.g. a `.png` file is really in the PNG format and "
                    + "not a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();

        String extension = getExtension(name);
        if (extension.isEmpty() || !isImageExtension(extension)) {
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return;
        }

        if (bytes.length < 4) {
            return;
        }

        String actualFormat = detectFormat(bytes);
        if (actualFormat == null) {
            return;
        }

        String normalizedExtension = normalizeExtension(extension);
        if (!normalizedExtension.equals(actualFormat)) {
            String message = String.format(
                    Locale.US,
                    "The file `%1$s` has a `.%2$s` extension but its contents look like a %3$s file.",
                    name,
                    extension,
                    actualFormat.toUpperCase(Locale.US)
            );
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static String getExtension(String filename) {
        if (filename.endsWith(".9.png")) {
            return "png";
        }
        int dot = filename.lastIndexOf('.');
        if (dot == -1 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static boolean isImageExtension(String extension) {
        return extension.equals("png")
                || extension.equals("gif")
                || extension.equals("jpg")
                || extension.equals("jpeg")
                || extension.equals("bmp")
                || extension.equals("webp")
                || extension.equals("xml");
    }

    private static String normalizeExtension(String extension) {
        if (extension.equals("jpeg")) {
            return "jpg";
        }
        return extension;
    }

    private static String detectFormat(byte[] data) {
        if (data.length >= 8
                && data[0] == (byte) 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A) {
            return "png";
        }

        if (data.length >= 6
                && data[0] == 0x47
                && data[1] == 0x49
                && data[2] == 0x46
                && data[3] == 0x38
                && (data[4] == 0x37 || data[4] == 0x39)
                && data[5] == 0x61) {
            return "gif";
        }

        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return "jpg";
        }

        if (data.length >= 2
                && data[0] == 0x42
                && data[1] == 0x4D) {
            return "bmp";
        }

        if (data.length >= 12
                && data[0] == 0x52
                && data[1] == 0x49
                && data[2] == 0x46
                && data[3] == 0x46
                && data[8] == 0x57
                && data[9] == 0x45
                && data[10] == 0x42
                && data[11] == 0x50) {
            return "webp";
        }

        if (looksLikeXml(data)) {
            return "xml";
        }

        return null;
    }

    private static boolean looksLikeXml(byte[] data) {
        for (byte b : data) {
            if (b == '<') {
                return true;
            }
            if (!isXmlWhitespace(b)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isXmlWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }
}