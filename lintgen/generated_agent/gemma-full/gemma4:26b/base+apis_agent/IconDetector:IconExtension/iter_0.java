package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.SimpleAttributeCheckingDetector;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Attr;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class IconDetector extends SimpleAttributeCheckingDetector {

    public static final String ERROR_MSG = "Icon format does not match the file extension";

    public static final Issue ISSUE = Issue.create(
            "IconFormatMismatch",
            ERROR_MSG,
            Issue.Level.ERROR,
            null,
            null,
            false
    );

    private static final List<String> EXTENSIONS = Arrays.asList("png", "webp", "jpg", "jpeg", "gif");

    @Override
    public List<String> getApplicableAttributes() {
        return Arrays.asList("android:src", "android:background", "android:drawable", "android:tint");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        String resourcePath = value.substring(1);
        int slashIndex = resourcePath.indexOf('/');
        if (slashIndex == -1) return;

        String type = resourcePath.substring(0, slashIndex);
        String name = resourcePath.substring(slashIndex + 1);

        if (!type.equals("drawable") && !type.equals("mipmap")) {
            return;
        }

        File xmlFile = context.getLocation(attribute).getFile();
        if (xmlFile == null) return;

        // The XML file is typically in res/layout/ or res/values/. 
        // We need to find the 'res' directory.
        File parentDir = xmlFile.getParentFile();
        if (parentDir == null) return;

        // If it's in res/layout, parent of layout is res.
        // If it's in res/values, parent of values is res.
        // We check if the parent directory name is 'layout', 'values', 'drawable', etc.
        File resDir = parentDir;
        if (parentDir.getName().equals("layout") || parentDir.getName().equals("values") 
                || parentintName(parentDir).equals("mipmap")) {
            resDir = parentDir.getParentFile();
        }

        if (resDir == null) return;

        // Check the base type folder (e.g., res/drawable)
        File typeFolder = new File(resDir, type);
        if (!typeFolder.exists() || !typeFolder.isDirectory()) {
            // If not in base, search for any qualifier folder starting with 'type' (e.g., drawable-hdpi)
            File[] folders = resDir.listFiles(f -> f.isDirectory() && f.getName().startsWith(type));
            if (folders != null && folders.length > 0) {
                // Check the first matching qualifier folder found
                checkFolderForResource(context, attribute, folders[0], name);
            }
        } else {
            checkFolderForResource(context, attribute, typeFolder, name);
        }
    }

    private String parentintName(File file) {
        return file.getName();
    }

    private void checkFolderForResource(XmlContext context, Attr attribute, File folder, String name) {
        for (String ext : EXTENSIONS) {
            File file = new File(folder, name + "." + ext);
            if (file.exists() && file.isFile()) {
                validateFileExtension(context, attribute, file, ext);
                return; 
            }
        }
    }

    private void validateFileExtension(XmlContext context, Attr attribute, File file, String extension) {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            byte[] header = new byte[12];
            int bytesRead = bis.read(header);
            if (bytesRead < 4) return;

            String detectedType = detectTypeFromMagicBytes(header);
            if (detectedType != null && !isExtensionMatch(detectedType, extension)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Icon format (" + detectedKSDetectedType(detectedType) + ") does not match the file extension (." + extension + ")"
                );
            }
        } catch (IOException e) {
            // Ignore IO errors during scanning
        }
    }

    private String detectTypeFromMagicBytes(byte[] header) {
        // PNG: 89 50 4E 47
        if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "png";
        }
        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        // GIF: 47 49 46
        if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
            return "gif";
        }
        // WEBP: RIFF .... WEBP
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F') {
            if (header.length >= 12 && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "webp";
            }
        }
        return null;
    }

    private String detectKSDetectedType(String type) {
        return type;
    }

    private boolean isExtensionMatch(String type, String extension) {
        if (type.equals("jpg") && (extension.equals("jpg") || extension.equals("jpeg"))) return true;
        return type.equals(extension);
    }
}