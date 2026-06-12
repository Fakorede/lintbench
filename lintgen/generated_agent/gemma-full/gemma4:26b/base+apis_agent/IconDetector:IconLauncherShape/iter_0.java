package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "LauncherIconSilhouette",
            "Launcher icon lacks a distinct silhouette",
            "According to the Android Design Guide, launcher icons should use a distinct silhouette " +
                    "(a three-dimensional, front view with depth). Using a solid color or a simple square " +
                    "for the foreground prevents this.",
            Issue.Severity.WARNING,
            true,
            null
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("adaptive-icon");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element child = (Element) node;
                String tagName = child.getLocalName();
                if (tagName == null) {
                    tagName = child.getTagName();
                }

                // We are looking for the <foreground> element within an <adaptive-icon>
                if ("foreground".equals(tagName)) {
                    // Check the android:drawable attribute
                    String drawableValue = child.getAttribute("android:drawable");
                    if (drawableValue.isEmpty()) {
                        drawableValue = child.getAttribute("drawable");
                    }

                    // If the foreground is explicitly a color resource, it cannot have a distinct silhouette
                    // as it will simply be a solid-colored square/circle matching the background.
                    if (drawableValue.startsWith("@color/")) {
                        context.report(
                                ISSUE,
                                child,
                                context.getLocation(child),
                                "The foreground of the adaptive icon is set to a color resource. " +
                                "A distinct silhouette requires a vector or bitmap with transparency."
                        );
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }
}