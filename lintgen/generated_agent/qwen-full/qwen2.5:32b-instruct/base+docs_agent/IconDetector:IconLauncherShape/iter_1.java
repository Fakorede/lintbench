package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class IconDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "LauncherIconShape",
            "The launcher icon shape should use a distinct silhouette, not just a filled square.",
            "According to the Android Design Guide, your launcher icons should \"use a distinct silhouette\", " +
                    "a \"three-dimensional, front view, with a slight perspective as if viewed from above, so that users perceive some depth.\"",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableResourceTypes() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE.getName());
    }

    @Override
    public void visitResource(@NonNull Context context, @NonNull Element element) {
        // Check for specific icon patterns that indicate a filled square.
        if (element.getNodeName().equals("shape") && hasFilledSquareShape(element)) {
            // Report the issue if it's a filled square shape.
            reportIssue(context, element);
        }
    }

    private boolean hasFilledSquareShape(@NonNull Element element) {
        // Implement logic to detect if the icon is a filled square
        // This could involve checking for specific XML attributes or elements that indicate a filled square shape.
        return false;  // Placeholder, replace with actual detection logic.
    }

    private void reportIssue(Context context, Element element) {
        // Report the issue at the given location in the resource file.
        context.report(ISSUE, element, context.getLocation(element),
                "Launcher icon should use a distinct silhouette and not just be a filled square.");
    }
}