package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Locale;

public class IconDetector extends ResourceFolderDetector {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                    + "really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkFile(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        String extension = getExtension(name).toLowerCase(Locale.ROOT);
        if (extension.isEmpty()) {
            return;
        }

        switch (extension) {
            case "png":
            case "gif":
            case "jpg":
            case "jpeg":
            case "webp":
            case "xml":
                break;
            default:
                return;
        }

        String format = detectFormat(file);
        if (format == null) {
            return;
        }

        if (!matchesExtension(format, extension)) {
            String message = String.format(
                    Locale.US,
                    "The file extension `.%1$s` does not match the file's format (%2$s)",
                    extension,
                    format.toUpperCase(Locale.US));
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1);
    }

    private static boolean matchesExtension(String format, String extension) {
        if (format.equals(extension)) {
            return true;
        }
        return "jpeg".equals(format)
                && ("jpg".equals(extension) || "jpeg".equals(extension));
    }

    private static String detectFormat(File file) {
        byte[] header = new byte[256];
        int read;
        try (FileInputStream fis = new FileInputStream(file)) {
            read = fis.read(header);
            if (read < 4) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }

        if (read >= 8
                && header[0] == (byte) 0x89
                && header[1] == 'P'
                && header[2] == 'N'
                && header[3] == 'G'
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return "png";
        }

        if (read >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return "gif";
        }

        if (header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8
                && header[2] == (byte) 0xFF) {
            return "jpeg";
        }

        if (read >= 12
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

        String text = new String(header, 0, read, StandardCharsets.UTF_8);
        String trimmed = text.trim();
        if (trimmed.startsWith("\uFEFF")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<")) {
            return "xml";
        }

        return null;
    }
}