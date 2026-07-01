package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class IconDetector extends Detector implements BinaryResourceScanner {

    private static final String PNG = "png";
    private static final String GIF = "gif";
    private static final String WEBP = "webp";
    private static final String JPG = "jpg";

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        Context ctx = (Context) context;
        File file = ctx.getFile();
        if (file == null || !file.isFile()) {
            return;
        }
        checkFile(context, file);
    }

    private void checkFile(@NotNull ResourceContext context, @NotNull File file) {
        String fileName = file.getName();
        String extension = getExtension(fileName);
        if (extension == null) {
            return;
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return;
        }

        if (data.length < 6) {
            return;
        }

        String actualFormat = detectFormat(data);
        if (actualFormat == null) {
            return;
        }

        if (!actualFormat.equalsIgnoreCase(extension)) {
            String message = String.format(
                    "The file extension is `.%1$s`, but the actual icon format is `%2$s`. "
                            + "Rename the file to `.%2$s` or convert it to the `%1$s` format.",
                    extension, actualFormat
            );
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return null;
        }
        String extension = fileName.substring(dot + 1);
        if (extension.equalsIgnoreCase("jpeg")) {
            return JPG;
        }
        return extension;
    }

    private static String detectFormat(byte[] data) {
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && (data[1] & 0xFF) == 0x50
                && (data[2] & 0xFF) == 0x4E
                && (data[3] & 0xFF) == 0x47
                && (data[4] & 0xFF) == 0x0D
                && (data[5] & 0xFF) == 0x0A
                && (data[6] & 0xFF) == 0x1A
                && (data[7] & 0xFF) == 0x0A) {
            return PNG;
        }

        if (data.length >= 6
                && data[0] == 'G'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == '8'
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a') {
            return GIF;
        }

        if (data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') {
            return WEBP;
        }

        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return JPG;
        }

        return null;
    }
}