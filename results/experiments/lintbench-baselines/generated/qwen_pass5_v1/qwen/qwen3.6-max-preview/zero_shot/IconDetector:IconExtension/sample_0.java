package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;
import java.util.Locale;

public class IconDetector extends Detector implements Detector.BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "IconExtension",
        "Icon format does not match the file extension",
        "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitBinaryResource(@NonNull ResourceContext context) {
        byte[] contents = context.getContents();
        if (contents == null || contents.length < 4) {
            return;
        }

        String name = context.getFile().getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == name.length() - 1) {
            return;
        }

        String ext = name.substring(dotIndex + 1).toLowerCase(Locale.US);
        String actualFormat = detectFormat(contents);
        if (actualFormat == null) {
            return;
        }

        boolean matches = false;
        switch (actualFormat) {
            case "png":
                matches = ext.equals("png");
                break;
            case "jpg":
                matches = ext.equals("jpg") || ext.equals("jpeg");
                break;
            case "gif":
                matches = ext.equals("gif");
                break;
            case "webp":
                matches = ext.equals("webp");
                break;
            case "bmp":
                matches = ext.equals("bmp");
                break;
        }

        if (!matches) {
            String message = String.format(
                "The file is actually a %s file but has the extension .%s",
                actualFormat.toUpperCase(Locale.US), ext);
            context.report(ISSUE, context.getLocation(), message);
        }
    }

    private static String detectFormat(byte[] bytes) {
        if (bytes.length >= 8 &&
            bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47 &&
            bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == (byte) 0x1A && bytes[7] == 0x0A) {
            return "png";
        }
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF) {
            return "jpg";
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38) {
            return "gif";
        }
        if (bytes.length >= 12 &&
            bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 &&
            bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return "webp";
        }
        if (bytes[0] == 0x42 && bytes[1] == 0x4D) {
            return "bmp";
        }
        return null;
    }
}