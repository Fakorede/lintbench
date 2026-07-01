package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            IconDetector.class,
            Scope.BINARY_RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            IMPLEMENTATION);

    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] GIF_MAGIC_87A = {
            'G', 'I', 'F', '8', '7', 'a'
    };
    private static final byte[] GIF_MAGIC_89A = {
            'G', 'I', 'F', '8', '9', 'a'
    };
    private static final byte[] JPEG_MAGIC = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };
    private static final byte[] WEBP_MAGIC_RIFF = {
            'R', 'I', 'F', 'F'
    };
    private static final byte[] WEBP_MAGIC_WEBP = {
            'W', 'E', 'B', 'P'
    };

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.getFile();
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1 || dot == name.length() - 1) {
            return;
        }

        String extension = name.substring(dot + 1).toLowerCase(Locale.US);

        byte[] magic = new byte[12];
        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            int read = stream.read(magic);
            if (read < 4) {
                return;
            }

            Format format = detectFormat(magic, read);
            if (format == null) {
                return;
            }

            if (!format.accepts(extension)) {
                String message = String.format(
                        "The file is a %1$s file but the extension is `.%2$s`",
                        format.name(),
                        extension);
                context.report(ISSUE, Location.create(file), message);
            }
        } catch (IOException e) {
            // Ignore files we cannot read.
        }
    }

    private enum Format {
        PNG("png"),
        GIF("gif"),
        JPEG("jpg", "jpeg"),
        WEBP("webp");

        private final String[] extensions;

        Format(String... extensions) {
            this.extensions = extensions;
        }

        boolean accepts(@NonNull String extension) {
            for (String ext : extensions) {
                if (ext.equals(extension)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Nullable
    private static Format detectFormat(@NonNull byte[] magic, int length) {
        if (length >= PNG_MAGIC.length && startsWith(magic, PNG_MAGIC)) {
            return Format.PNG;
        }
        if (length >= GIF_MAGIC_87A.length
                && (startsWith(magic, GIF_MAGIC_87A) || startsWith(magic, GIF_MAGIC_89A))) {
            return Format.GIF;
        }
        if (length >= JPEG_MAGIC.length && startsWith(magic, JPEG_MAGIC)) {
            return Format.JPEG;
        }
        if (length >= 12
                && startsWith(magic, WEBP_MAGIC_RIFF)
                && arrayRegionEquals(magic, 8, WEBP_MAGIC_WEBP, 0, WEBP_MAGIC_WEBP.length)) {
            return Format.WEBP;
        }
        return null;
    }

    private static boolean startsWith(@NonNull byte[] data, @NonNull byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean arrayRegionEquals(@NonNull byte[] data, int dataOffset,
            @NonNull byte[] other, int otherOffset, int length) {
        for (int i = 0; i < length; i++) {
            if (data[dataOffset + i] != other[otherOffset + i]) {
                return false;
            }
        }
        return true;
    }
}