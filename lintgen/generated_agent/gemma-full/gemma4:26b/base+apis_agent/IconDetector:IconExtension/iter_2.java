package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconFormatMismatch",
            "Icon format does not match the file extension",
            Issue.Severity.ERROR,
            null,
            null,
            false
    );

    private static final List<String> ATTRIBUTES = Arrays.asList("android:src", "android:background", "android:drawable", "android:tint");
    private static final List<String> EXTENSIONS = Arrays.asList("png", "webp", "jpg", "jpeg", "gif");

    @Override
    public Collection<String> getApplicableAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        String resourcePath = value.substring(1);
        int slashIndex = resourcePath.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String type = resourcePath.substring(0, slashIndex);
        String name = resourcePath.substring(slashIndex + 1);

        if (!type.equals("drawable") && !type.equals("mipmap")) {
            return;
        }

        File xmlFile = context.getLocation(attribute).getFile();
        if (xmlFile == null) {
            return;
        }

        File resDir = findResDir(xmlFile);
        if (resDir == null) {
            return;
        }

        checkResourceFiles(context, attribute, resDir, type, name);
    }

    private File findResDir(File file) {
        File current = file.getParentFile();
        while (current != null) {
            if (current.getName().equals("res")) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private void checkResourceFiles(XmlContext context, Attr attribute, File resDir, String type, String name) {
        File[] folders = resDir.listFiles(f -> f.isDirectory() && (f.getName().equals(type) || f.getName().startsWith(type + "-")));
        if (folders == null) {
            return;
        }

        for (File folder : folders) {
            for (String ext : EXTENSIONS) {
                File file = new File(folder, name + "." + ext);
                if (file.exists() && file.isFile()) {
                    validateFileExtension(context, attribute, file, ext);
                }
            }
        }
    }

    private void validateFileExtension(XmlContext context, Attr attribute, File file, String extension) {
        byte[] header = new byte[12];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = fis.read(header);
            if (bytesRead < 4) {
                return;
            }

            String detectedType = detectTypeFromMagicBytes(header);
            if (detectedType != null && !isExtensionMatch(detectedType, extension)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Icon format (" + detectedType + ") does not match the file extension (." + extension + ")"
                );
            }
        } catch (IOException e) {
            // Ignore IO errors during scanning
        }
    }

    private String detectTypeFromMagicBytes(byte[] header) {
        // PNG: 89 50 4E 41
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
        // WebP: RIFF .... WEBP
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private boolean isExtensionMatch(String detectedType, String extension) {
        if (detectedType.equals("jpg") && (extension.equals("jpg") || extension.equals("jpeg"))) {
            return true;
        }
        return detectedType.equals(extension);
    }
}