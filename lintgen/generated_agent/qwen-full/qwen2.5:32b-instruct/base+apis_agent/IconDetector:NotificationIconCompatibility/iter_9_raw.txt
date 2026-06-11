package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "NotificationIconCompat",
            "Notification icons should define a raster image to support Android versions below 5.0 (API 21).",
            "Note that the way Lint decides whether an icon is a notification icon is based on the filename prefix `ic_stat_`.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Collections.emptySet())
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");

        if (name != null && name.startsWith("ic_stat_")) {
            boolean hasRasterImage = false;

            for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                Node childNode = element.getChildNodes().item(i);
                if (childNode instanceof Element) {
                    Element childElement = (Element) childNode;
                    String nodeName = childElement.getTagName();
                    if ("bitmap".equals(nodeName)) {
                        hasRasterImage = true;
                        break;
                    }
                }
            }

            if (!hasRasterImage) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Notification icon should define a raster image to support Android versions below 5.0 (API 21).");
            }
        }
    }
}