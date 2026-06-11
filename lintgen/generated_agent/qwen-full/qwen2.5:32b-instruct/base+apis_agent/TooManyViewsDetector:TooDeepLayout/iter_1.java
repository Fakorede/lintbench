package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Scope;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.Map;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout hierarchy is too deep",
            "Nested layouts can be bad for performance. Consider using a flatter layout (such as RelativeLayout or GridLayout).",
            Category.PERFORMANCE,
            6, 5,
            new Implementation(TooManyViewsDetector.class, Scope.ALL_RESOURCE_FILES));

    private static final int DEFAULT_MAX_DEPTH = 10;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Track the depth of the layout hierarchy.
        Integer currentDepth = (Integer) context.getExtras().get("depth");
        if (currentDepth == null) {
            currentDepth = 0;
        } else {
            currentDepth++;
        }

        int maxDepth = DEFAULT_MAX_DEPTH;
        String envMaxDepth = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (envMaxDepth != null) {
            try {
                maxDepth = Integer.parseInt(envMaxDepth);
            } catch (NumberFormatException e) {
                // Ignore invalid environment variable value
            }
        }

        if (currentDepth > maxDepth) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Layout hierarchy is too deep. Consider using a flatter layout.");
        }

        Map<String, Object> extras = context.getExtras();
        extras.put("depth", currentDepth);
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        // Reset the depth when moving up in the hierarchy.
        Integer currentDepth = (Integer) context.getExtras().get("depth");
        if (currentDepth != null && currentDepth > 0) {
            Map<String, Object> extras = context.getExtras();
            extras.put("depth", currentDepth - 1);
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}