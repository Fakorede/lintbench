package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.BinaryFileScanner {

    public static final Issue ICON_EXTENSION = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.BINARY_RESOURCE_FILE))
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryFile(@NonNull Context context, @NonNull File file, @NonNull byte[] content) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return;
        }

        String extension = name.substring(dot + 1).toLowerCase();
        String actualFormat = detectFormat(content);
        if (actualFormat == null) {
            return;
        }

        if (extensionMatches(actualFormat, extension)) {
            return;
        }

        String message = String.format(
                "Icon format `%s` does not match file extension `.%s`",
                actualFormat, extension);
        context.report(ICON_EXTENSION, Location.create(file), message);
    }

    private static String detectFormat(@NonNull byte[] content) {
        if (content.length < 4) {
            return null;
        }

        if (startsWith(content, 0x89, 0x50, 0x4E, 0x47)) {
            return "png";
        }
        if (content.length >= 3
                && content[0] == (byte) 0xFF
                && content[1] == (byte) 0xD8
                && content[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (startsWith(content, 0x47, 0x49, 0x46, 0x38)) {
            return "gif";
        }
        if (content.length >= 2
                && content[0] == (byte) 0x42
                && content[1] == (byte) 0x4D) {
            return "bmp";
        }
        if (content.length >= 12
                && startsWith(content, 0x52, 0x49, 0x46, 0x46)
                && content[8] == (byte) 0x57
                && content[9] == (byte) 0x45
                && content[10] == (byte) 0x42
                && content[11] == (byte) 0x50) {
            return "webp";
        }

        return null;
    }

    private static boolean extensionMatches(@NonNull String format, @NonNull String extension) {
        if (format.equals(extension)) {
            return true;
        }
        return "jpg".equals(format) && ("jpeg".equals(extension) || "jpg".equals(extension));
    }

    private static boolean startsWith(@NonNull byte[] content, int... magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != (byte) magic[i]) {
                return false;
            }
        }
        return true;
    }
}