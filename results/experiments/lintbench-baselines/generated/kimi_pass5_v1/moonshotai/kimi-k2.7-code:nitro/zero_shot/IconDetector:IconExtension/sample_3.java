package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

public class IconDetector extends ResourceFolderDetector {

    public static final Issue ICON_EXTENSION = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "The file extension of an icon should match its actual image format. "
                    + "For example, a `.png` file should really be a PNG image, not a "
                    + "different format renamed with a `.png` extension.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkResourceFile(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        String extension = getExtension(name);
        String expectedFormat;
        switch (extension) {
            case "png":
                expectedFormat = "png";
                break;
            case "jpg":
            case "jpeg":
                expectedFormat = "jpg";
                break;
            case "gif":
                expectedFormat = "gif";
                break;
            case "webp":
                expectedFormat = "webp";
                break;
            case "bmp":
                expectedFormat = "bmp";
                break;
            default:
                return;
        }

        byte[] header = new byte[12];
        int read;
        try (FileInputStream fis = new FileInputStream(file)) {
            read = fis.read(header);
        } catch (IOException e) {
            return;
        }

        if (read < 3) {
            return;
        }

        String actualFormat = getActualFormat(header, read);
        if (actualFormat == null) {
            return;
        }

        if (!actualFormat.equals(expectedFormat)) {
            Location location = context.getLocation();
            context.report(
                    ICON_EXTENSION,
                    location,
                    String.format(
                            "The image format is `%1$s` but the file extension is `.%2$s`",
                            actualFormat,
                            extension
                    )
            );
        }
    }

    @NonNull
    private static String getExtension(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static String getActualFormat(@NonNull byte[] header, int read) {
        if (read >= 8
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

        if (read >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return "gif";
        }

        if (read >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
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

        if (read >= 2 && header[0] == 'B' && header[1] == 'M') {
            return "bmp";
        }

        return null;
    }
}