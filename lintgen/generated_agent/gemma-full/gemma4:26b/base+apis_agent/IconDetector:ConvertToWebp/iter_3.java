package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
            10,
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
        } else if ("adaptive-icon".equals(tagName)) {
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
        if (value == null || value.isEmpty()) {
            if (attributeName.contains(":")) {
                value = element.getAttribute(attributeName.split(":")[1]);
            } else {
                return;
            }
        }

        if (!value.startsWith("@")) {
            return;
        }

        String resourcePath = value.substring(1);
        String[] parts = resourcePath.split("/");
        if (parts.length < 2) {
            return;
        }

        String type = parts[0];
        String name = parts[1];

        try {
            String uri = element.getOwnerDocument().getBaseURI();
            if (uri == null || uri.isEmpty()) {
                return;
            }

            File xmlFile = new File(uri.replace("file:", ""));
            File resDir = findResDir(xmlFile);
            if (resDir != null) {
                String[] extensions = {".png", ".jpg", ".jpeg"};
                for (String ext : extensions) {
                    if (checkFileExistsInResourceFolders(resDir, type, name, ext)) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "The icon resource '" + value + "' is a PNG/JPEG and should be converted to WebP.");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors in path resolution
        }
    }

    private boolean checkFileExistsInResourceFolders(File resDir, String type, String name, String ext) {
        File[] subDirs = resDir.listFiles();
        if (subDirs == null) return false;

        for (File dir : subDirs) {
            if (dir.isDirectory()) {
                String dirName = dir.getName();
                if (dirName.equals(type) || dirName.startsWith(type + "-")) {
                    File file = new File(dir, name + ext);
                    if (file.exists()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private File findResDir(File start) {
        File current = start;
        while (current != null) {
            if ("res".equals(current.getName())) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }
}