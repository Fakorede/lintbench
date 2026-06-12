package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertIconToWebP",
            "The icon resource can be converted to WebP for better compression.",
            "Converting PNG or JPEG icons to WebP can significantly reduce APK size.",
            Category.CORRECTNESS,
            0, // Priority dummy value to satisfy the 7-argument requirement found in some environments
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList("bitmap", "adaptive-icon");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("bitmap".equals(tagName)) {
            checkAttribute(context, element, "android:src");
        } else if ("adaptive-can".equals(tagName) || "adaptive-icon".equals(tagName)) {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element) {
                    Element child = (Element) children.item(i);
                    String childTagName = child.getTagName();
                    if ("background".equals(childTagName) || "foreground".equals(childTagName)) {
                        checkAttribute(context, child, "android:drawable");
                    }
                }
            }
        }
    }

    private void checkAttribute(XmlContext context, Element element, String attributeName) {
        String value = element.getAttribute(attributeName);
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Value is like @mipmap/ic_launcher or @drawable/ic_bg
        String[] parts = value.substring(1).split("/");
        if (parts.length != 2) {
            return;
        }

        String typeStr = parts[0];
        String name = parts[1];

        try {
            ResourceType resourceType = ResourceType.valueOf(typeStr.toUpperCase());
            File baseDir = context.getAppResources().getResourceFolder(resourceType);
            if (baseDir == null) {
                return;
            }

            if (isPngOrJpegAndNotWebp(context, resourceType, name)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "The icon resource '" + value + "' is a PNG/JPEG and should be converted to WebP.");
            }
        } catch (IllegalArgumentException e) {
            // Not a standard drawable or mipmap type
        }
    }

    private boolean isPngOrJpegAndNotWebp(XmlContext context, ResourceType resourceType, String name) {
        File baseDir = context.getAppResources().getResourceFolder(resourceType);
        if (baseDir == null) return false;

        final boolean[] found = new boolean[3]; // 0: png, 1: jpeg, 2: webp
        searchFiles(baseDir, name, found);
        return (found[0] || found[1]) && !found[2];
    }

    private void searchFiles(File dir, String name, boolean[] found) {
        if (!dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                searchFiles(file, name, found);
            } else {
                String fileName = file.getName().toLowerCase();
                // Check if the file matches the resource name and has a relevant extension
                if (fileName.startsWith(name + ".")) {
                    if (fileName.endsWith(".png")) {
                        found[0] = true;
                    } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                        found[1] = true;
                    } else if (fileName.endsWith(".webp")) {
                        found[2] = true;
                    }
                }
            }
        }
    }
}