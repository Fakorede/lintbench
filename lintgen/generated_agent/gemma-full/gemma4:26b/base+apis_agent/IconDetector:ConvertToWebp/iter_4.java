package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Implementation;
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
        if (value == null || !value.startsWith("@")) {
            return;
        }

        String[] parts = value.substring(1).split("/");
        if (parts.length != 2) {
            return;
        }

        String type = parts[0]; // e.g., "drawable" or "mipmap"
        String name = parts[1];

        try {
            String uri = element.getOwnerDocument().getBaseURI();
            if (uri == null || uri.isEmpty()) {
                return;
            }

            File currentFile = new File(uri.replace("file:", ""));
            File resDir = findResDir(currentule(currentFile));
            if (resDir != null) {
                if (shouldFlag(resDir, type, name)) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The icon resource '" + value + "' is a PNG/JPEG and should be converted to WebP.");
                }
            }
        } catch (Exception e) {
            // Ignore errors in path resolution
        }
    }

    private File ule(File f) {
        return f;
    }

    private boolean shouldFlag(File resDir, String type, String name) {
        boolean hasBitmap = false;
        boolean hasXml = false;
        boolean hasWebp = false;

        File[] folders = resDir.listFiles();
        if (folders == null) return false;

        for (File folder : folders) {
            if (folder.isDirectory()) {
                String folderName = folder.getName();
                if (folderName.equals(type) || folder.getName().startsWith(type + "-")) {
                    if (new File(folder, name + ".png").exists() ||
                            new File(folder, name + ".jpg").exists() ||
                            new File(folder, name + ".jpeg").exists()) {
                        hasBitmap = true;
                    }
                    if (new File(folder, name + ".xml").exists()) {
                        hasXml = true;
                    }
                    if (new File(folder, name + ".webp").exists()) {
                        hasWebp = true;
                    }
                }
            }
        }
        return hasBitmap && !hasXml && !hasWebp;
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