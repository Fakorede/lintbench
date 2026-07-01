package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class IconDetector extends Detector implements BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
        "IconExtension",
        "Icon format does not match the file extension",
        "Ensures that icons have the correct file extension (e.g. a `.png` file is really in the PNG format and not for example a GIF file named `.png`).",
        Category.ICONS,
        5,
        Severity.WARNING,
        new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.getFile();
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) return;

        String ext = name.substring(dot + 1).toLowerCase();
        if (!isImageExtension(ext)) return;

        File parent = file.getParentFile();
        if (parent == null) return;
        String folderName = parent.getName();
        if (!folderName.startsWith("drawable") && !folderName.startsWith("mipmap")) return;

        String actualFormat = detectFormat(file);
        if (actualFormat == null) return;

        String normalizedExt = ext.equals("jpeg") ? "jpg" : ext;
        if (!actualFormat.equals(normalizedExt)) {
            String message = String.format(
                "The file is named .%s but appears to be a %s file",
                ext, actualFormat.toUpperCase()
            );
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static boolean isImageExtension(String ext) {
        return ext.equals("png") || ext.equals("gif") || ext.equals("jpg") ||
               ext.equals("jpeg") || ext.equals("webp") || ext.equals("bmp");
    }

    private static String detectFormat(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = fis.read(header);
            if (read < 4) return null;

            if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return "png";
            }
            if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x38) {
                return "gif";
            }
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return "jpg";
            }
            if (header[0] == 0x42 && header[1] == 0x4D) {
                return "bmp";
            }
            if (read >= 12 && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46 &&
                header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
                return "webp";
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}