package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;

public class IconDetector extends Detector implements BinaryResourceScanner {

    private static final String PNG = "png";
    private static final String GIF = "gif";
    private static final String JPG = "jpg";
    private static final String WEBP = "webp";
    private static final String BMP = "bmp";

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                    + "really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, EnumSet.of(Scope.BINARY_RESOURCE_FILE))
    );

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        File file = context.getFile();
        String name = file.getName();
        String extension = getExtension(name);
        if (extension == null) {
            return;
        }

        byte[] header = readHeader(file);
        if (header == null) {
            return;
        }

        String format = detectFormat(header);
        if (format == null) {
            return;
        }

        if (!format.equals(extension)) {
            String message = String.format(
                    "The icon `%1$s` appears to be a %2$s file but uses the `.%3$s` extension",
                    name, format.toUpperCase(Locale.US), extension);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    @Nullable
    private static String getExtension(@NotNull String name) {
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return null;
        }
        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        if (ext.equals("jpeg")) {
            ext = JPG;
        }
        if (ext.equals(PNG) || ext.equals(GIF) || ext.equals(JPG)
                || ext.equals(WEBP) || ext.equals(BMP)) {
            return ext;
        }
        return null;
    }

    @Nullable
    private static byte[] readHeader(@NotNull File file) {
        byte[] header = new byte[12];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(header);
            if (read < 6) {
                return null;
            }
            if (read < header.length) {
                byte[] truncated = new byte[read];
                System.arraycopy(header, 0, truncated, 0, read);
                return truncated;
            }
            return header;
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private static String detectFormat(@NotNull byte[] header) {
        if (header.length >= 8
                && header[0] == (byte) 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return PNG;
        }

        if (header.length >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return GIF;
        }

        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return JPG;
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
            return WEBP;
        }

        if (header.length >= 2 && header[0] == 'B' && header[1] == 'M') {
            return BMP;
        }

        return null;
    }
}