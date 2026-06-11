package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.EnumSet;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "LauncherIconShape",
            "The launcher icon shape should use a distinct silhouette.",
            "According to the Android Design Guide, your launcher icons should \"use a distinct silhouette\", " +
                    "a \"three-dimensional, front view, with a slight perspective as if viewed from above, so that users perceive some depth.\" " +
                    "This implies that your launcher icon should not be a filled square.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE))
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.DRAWABLE == folderType;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String nodeName = element.getNodeName();
        if ("item".equals(nodeName)) {
            Attr iconAttr = element.getAttributeNode("android:icon");
            if (iconAttr != null && isFilledSquare(context, iconAttr.getValue())) {
                context.report(ISSUE, iconAttr, context.getLocation(iconAttr),
                        "Launcher icons should use a distinct silhouette and not be a filled square.");
            }
        }
    }

    private boolean isFilledSquare(XmlContext context, String iconName) {
        // This is a placeholder for the actual logic to determine if the icon is a filled square.
        // In practice, you would analyze the drawable resource or its metadata to make this determination.
        return false;
    }
}