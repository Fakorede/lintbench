package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout " +
            "(such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be " +
            "configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final int DEFAULT_MAX_DEPTH = 10;
    private int maxDepth = -1;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != ResourceFolderType.LAYOUT) {
            return;
        }

        int depth = getDepth(element);
        int limit = getMaxDepth();

        if (depth > limit) {
            context.report(ISSUE, context.getLocation(element),
                    "Layout hierarchy is too deep: depth is " + depth + " (max allowed is " + limit + ")");
        }
    }

    private static int getDepth(Element element) {
        int depth = 0;
        Node node = element;
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                depth++;
            }
            node = node.getParentNode();
        }
        return depth;
    }

    private int getMaxDepth() {
        if (maxDepth == -1) {
            String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
            if (env != null) {
                try {
                    maxDepth = Integer.parseInt(env);
                } catch (NumberFormatException e) {
                    maxDepth = DEFAULT_MAX_DEPTH;
                }
            } else {
                maxDepth = DEFAULT_MAX_DEPTH;
            }
        }
        return maxDepth;
    }
}