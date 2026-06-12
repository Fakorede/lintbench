package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.SimpleXmlDetector;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IconDetector extends SimpleXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ConvertIconToWebP",
            "The icon resource can be converted to WebP for better compression.",
            Issue.Level.SUGGESTION,
            null,
            Collections.emptyList(),
            false
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

        // Extract type and name from "@type/name" (e.g., "@mipmap/ic_launcher")
        String[] parts = value.substring(1).split("/");
        if (parts.length < 2) {
            return;
        }

        String type = parts[0];
        String name = parts[1];

        if (isPngOrJpg(context, type, name)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The icon resource '" + value + "' is a PNG/JPEG and should be converted to WebP.");
        }
    }

    private boolean isPngOrJpg(XmlContext context, String type, String name) {
        File resDir = context.getEvaluationContext().getProject().getResDir();
        if (resDir == null) {
            return false;
        }

        // Check if any file with this name and .png or .jpg extension exists in the resource folders.
        return searchInResFolders(resDir, type, name, ".png") ||
               searchInResFolders(resDir, type, name, ".jpg");
    }

    private boolean searchInResFolders(File resDir, String type, String name, String extension) {
        return findFileRecursive(resDir, type, name, extension);
    }

    private boolean findFileRecursive(File dir, String type, String name, String ext) {
        if (!dir.isDirectory()) {
            return false;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // Check if this directory is a resource folder for the target type (e.g., 'mipmap-hdpi')
                if (file.getName().startsWith(type)) {
                    // Check if name + extension exists directly in this specific qualifier folder
                    File target = new File(file, name + ext);
                    if (target.exists()) {
                        return true;
                    }
                }
                // Continue searching deeper for other qualifiers or subdirectories
                if (findFileRecursive(file, type, name, ext)) {
                    return true;
                }
            }
        }
        return false;
    }
}