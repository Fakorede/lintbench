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
        Node parent = element.getParentNode();
        if (!(parent instanceof Element)) {
            return;
        }
        Element parentElement = (Element) parent;

        String parentTagName = parentElement.getLocalName();
        if (parentTagName == null) {
            parentTagName = parentElement.getTagName();
        }

        if (!"adaptive-icon".equals(parentTagName)) {
            return;
        }

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
        return true;
    }
}