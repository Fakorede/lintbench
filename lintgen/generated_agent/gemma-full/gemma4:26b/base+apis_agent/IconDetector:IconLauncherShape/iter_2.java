package com.android.tools.lint.checks;

import java.util.Collection;
import java.util.Collections;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "LauncherIconSilhouette",
            "Launcher icon lacks a distinct silhouette",
            "According to the Android Design Guide, launcher icons should use a distinct silhouette " +
                    "(a three-dimensional, front view with depth). Using a solid color for the foreground " +
                    "prevents this.",
            Issue.Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("foreground");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getParentNode() instanceof Element) {
            Element parent = (Element) element.getParentNode();
            String parentName = parent.getLocalName();
            if (parentName == null) {
                parentName = parent.getTagName();
            }

            if ("adaptive-icon".equals(parentName)) {
                // Check for android:drawable attribute
                String drawable = element.getAttribute("android:drawable");
                if (drawable == null || drawable.isEmpty()) {
                    drawable = element.getAttribute("drawable");
                }

                // If the foreground is a color resource, it lacks a distinct silhouette
                if (drawable != null && drawable.startsWith("@color/")) {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "The foreground of the adaptive icon is set to a color resource. " +
                                    "A distinct silhouette requires a vector or bitmap with transparency."
                    );
                }
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.XML;
    }
}