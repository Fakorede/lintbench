package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match file extension",
            "Ensures that icons have the correct file extension. For example, a `.png` file "
                    + "should really be in PNG format, not a GIF file renamed with a `.png` "
                    + "extension.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE)
    );

    @Nullable
    @Override
    public Set<ResourceType> appliesTo(@NotNull ResourceFolderType folderType) {
        return EnumSet.of(ResourceType.DRAWABLE, ResourceType.MIPMAP);
    }

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        String fileName = context.getFile().getName();
        String expectedFormat = getExtensionFormat(fileName);
        if (expectedFormat == null) {
            return;
        }

        byte[] header = new byte[12];
        try (FileInputStream fis = new FileInputStream(context.getFile())) {
            int read = fis.read(header);
            if (read < 6) {
                return;
            }
        } catch (IOException e) {
            return;
        }

        String actualFormat = detectImageFormat(header);
        if (actualFormat == null) {
            return;
        }

        if (!actualFormat.equals(expectedFormat)) {
            String message = String.format(
                    "The icon `%1$s` appears to be a %2$s file but uses the `.%3$s` extension",
                    fileName, actualFormat.toUpperCase(Locale.ROOT), expectedFormat);
            context.report(ISSUE, Location.create(context.getFile()), message);
        }
    }

    @Nullable
    private static String getExtensionFormat(@NotNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return null;
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "png":
                return "png";
            case "gif":
                return "gif";
            case "jpg":
            case "jpeg":
                return "jpeg";
            case "webp":
                return "webp";
            case "bmp":
                return "bmp";
            default:
                return null;
        }
    }

    @Nullable
    private static String detectImageFormat(@NotNull byte[] header) {
        if (header.length >= 8
                && header[0] == (byte) 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return "png";
        }

        if (header.length >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return "gif";
        }

        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }

        if (header.length >= 12
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

        if (header.length >= 2 && header[0] == 'B' && header[1] == 'M') {
            return "bmp";
        }

        return null;
    }
}