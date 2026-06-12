package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
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
    public void visitDocument(XmlContext context, Document document) {
        // No specific logic needed for the whole document.
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                Element child = (Element) children.item(i);
                String tagName = child.getLocalName();

                // Fallback if getLocalName is null
                if (tagName == null) {
                    tagName = child.getTagName();
                }

                // We are looking for the <foreground> element within an <adaptive-icon>
                if ("foreground".equals(tagName)) {
                    // Check the android:drawable attribute
                    String drawableValue = child.getAttribute("android:drawable");
                    
                    // If namespace is not handled, check 'drawable' as well
                    if (drawableValue == null || drawableValue.isEmpty()) {
                        drawableValue = child.getAttribute("drawable");
                    }

                    // If the foreground is explicitly a color resource, it cannot have a distinct silhouette
                    // as it will simply be a solid-colored square/circle matching the background.
                    if (drawableValue != null && drawableValue.startsWith("@color/")) {
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
        // Adaptive icons are typically defined in drawable or mipmap folders.
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.XML;
    }
}