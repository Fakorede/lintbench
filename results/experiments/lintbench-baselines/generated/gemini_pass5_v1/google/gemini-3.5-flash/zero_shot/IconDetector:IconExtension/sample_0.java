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

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.BINARY_RESOURCE_FILE_SCOPE)
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
        if (!ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("gif") && !ext.equals("webp")) {
            return;
        }

        byte[] bytes;
        try {
            bytes = context.getClient().readBytes(file);
        } catch (IOException e) {
            return;
        }

        if (bytes.length < 4) {
            return;
        }

        String actualFormat = getFormat(bytes);
        if (actualFormat == null) {
            return;
        }

        boolean mismatch = false;
        if (ext.equals("png") && !actualFormat.equals("png")) {
            mismatch = true;
        } else if ((ext.equals("jpg") || ext.equals("jpeg")) && !actualFormat.equals("jpeg")) {
            mismatch = true;
        } else if (ext.equals("gif") && !actualFormat.equals("gif")) {
            mismatch = true;
        } else if (ext.equals("webp") && !actualFormat.equals("webp")) {
            mismatch = true;
        }

        if (mismatch) {
            String message = String.format("Misleading file extension; what looks like a `.%s` file is actually a `%s` file", ext, actualFormat);
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static String getFormat(byte[] bytes) {
        if (bytes.length >= 4 &&
                bytes[0] == (byte) 0x89 &&
                bytes[1] == (byte) 0x50 &&
                bytes[2] == (byte) 0x4E &&
                bytes[3] == (byte) 0x47) {
            return "png";
        }

        if (bytes.length >= 3 &&
                bytes[0] == (byte) 0xFF &&
                bytes[1] == (byte) 0xD8 &&
                bytes[2] == (byte) 0xFF) {
            return "jpeg";
        }

        if (bytes.length >= 3 &&
                bytes[0] == (byte) 'G' &&
                bytes[1] == (byte) 'I' &&
                bytes[2] == (byte) 'F') {
            return "gif";
        }

        if (bytes.length >= 12 &&
                bytes[0] == (byte) 'R' &&
                bytes[1] == (byte) 'I' &&
                bytes[2] == (byte) 'F' &&
                bytes[3] == (byte) 'F' &&
                bytes[8] == (byte) 'W' &&
                bytes[9] == (byte) 'E' &&
                bytes[10] == (byte) 'B' &&
                bytes[11] == (byte) 'P') {
            return "webp";
        }

        if (bytes.length >= 2 &&
                bytes[0] == (byte) 'B' &&
                bytes[1] == (byte) 'M') {
            return "bmp";
        }

        return null;
    }
}