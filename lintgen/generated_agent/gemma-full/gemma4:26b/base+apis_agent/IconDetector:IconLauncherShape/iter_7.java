package com.android.tools.lint.checks;

import java.util.Collection;
import java.util.Collections;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "LauncherIconSilhouette",
            "Launcher icon lacks a distinct silhouette",
            "According to the Android Design Guide, launcher icons should use a \"distinct silhouette\" " +
                    "(a three-dimensional, front view with depth). Using a solid color for the foreground " +
                    "prevents this.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("foreground");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if the current element is <foreground>
        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }
        if (!"foreground".equals(tagName)) {
            return;
        }

        // Check if the parent element is <adaptive-icon>
        Node parent = element.getParentNode();
        if (parent == null) {
            return;
        }
        String parentName = parent.getLocalName();
        if (parentName == null) {
            parentName = parent.getTagName();
        }
        if (!"adaptive-icon".equals(parentName)) {
            return;
        }

        // Iterate through attributes to find 'android:drawable' or 'drawable'
        // that points to a color resource, which would result in a solid square.
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attr = (Attr) element.getAttributes().item(i);
            String attrName = attr.getName();
            String attrValue = attr.getValue();

            if (attrName.endsWith("drawable") && (attrValue.startsWith("@color/") || attrValue.startsWith("@android:color/"))) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The foreground of the adaptive icon is set to a color resource. " +
                                "A distinct silhouette requires a vector or bitmap with transparency."
                );
                return;
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        // Apply to all resource folders that might contain adaptive-icon XMLs (mipmap, drawable, etc.)
        return true;
    }
}