package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertIconToWebP",
            "The icon resource can be converted to WebP for better compression.",
            Severity.WARNING,
            false,
            10,
            Collections.singletonList(new Implementation(IconDetector.class, null, null))
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
            String localName = attributeName.contains(":") ? attributeName.split(":")[1] : attributeName;
            value = element.getAttribute(localName);
        }

        if (value == null || value.isEmpty() || !value.startsWith("@")) {
            return;
        }

        String[] parts = value.substring(1).split("/");
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

            File xmlFile = new File(uri);
            File resDir = findResDir(xmlFile);
            if (resDir != null) {
                File pngFile = new File(resDir, type + "/" + name + ".png");
                File jpgFile = new File(resDir, type + "/" + name + ".jpg");

                if (pngFile.exists() || jpgFile.exists()) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "The icon resource '" + value + "' is a PNG/JPEG and should be converted to WebP.");
                }
            }
        } catch (Exception e) {
            // Ignore errors in path resolution or URI access
        }
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