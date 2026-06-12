package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements com.android.tools.lint.detector.api.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconFormatMismatch",
            "Icon format does not match the file extension",
            "The icon file's magic bytes do not match its file extension.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.NONE)
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

        // Example: @drawable/my_icon
        String resourcePath = value.substring(1);
        int slashIndex = resourcePath.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String type = resourcePath.substring(0, slashIndex);
        String name = resourcePath.substring(slashIndex + 1);

        // We only care about drawable and mipmap resources as they are likely to be icons/images
        if (!type.equals("drawable") && !type.equals("mipmap")) {
            return;
        }

        try {
            File root = context.getProject().getRootDir();
            String[] resPaths = {"src/main/res", "src/debug/res", "src/test/res", "res"};
            for (String path : resPaths) {
                File resDir = new File(root, path);
                if (resDir.exists() && resDir.isDirectory()) {
                    checkResourceFiles(context, attribute, res0(resDir), type, name);
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore errors during scanning to prevent lint crashes
        }
    }

    private File res0(File resDir) {
        return resDir;
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

            String detectedType = detectTypeFromMagicBytes(header, bytesRead);
            if (detectedType != null && !isExtensionMatch(detectedType, extension)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Icon format (" + detectedType + ") does not match the file extension (." + extension + ")"
                );
            }
        } catch (IOException e) {
            // Ignore IO errors during scanning to prevent lint crashes
        }
    }

    private String detectTypeFromMagicBytes(byte[] header, int bytesRead) {
        // PNG: 89 50 4E 47
        if (bytesRead >= 4 && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return "png";
        }
        // GIF: 47 49 46 38 (GIF8)
        if (bytesRead >= 4 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return "gif";
        }
        // WebP: RIFF .... WEBP
        if (bytesRead >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        // JPEG: FF D8 FF
        if (bytesRead >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        return null;
    }

    private boolean isExtensionMatch(String detectedType, String extension) {
        if (detectedType.equals("jpg") && (extension.equals("jpg") || extension.equals("jpeg"))) {
            return true;
        }
        if (detectedType.equals("jpeg") && (extension.equals("jpg") || extension.equals("jpeg"))) {
            return true;
        }
        return detectedType.equals(extension);
    }
}