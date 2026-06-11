package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "LauncherIconShape",
            "The launcher icon shape should use a distinct silhouette.",
            "According to the Android Design Guide, your launcher icons should \"use a distinct silhouette\", " +
                    "a \"three-dimensional, front view, with a slight perspective as if viewed from above, so that users perceive some depth.\" " +
                    "This implies that your launcher icon should not be a filled square.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
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
        if (name != null && name.startsWith("ic_launcher")) {
            String format = element.getAttribute("format");
            if ("png".equals(format)) {
                // Check for the presence of a silhouette in the icon.
                // This is a placeholder for actual image analysis logic.
                context.report(ISSUE, element, context.getLocation(element),
                        "Launcher icon should use a distinct silhouette.");
            }
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // No-op
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        // No-op
    }
}