package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class TooManyViewsDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
        "TooDeepLayout",
        "Layout hierarchy is too deep",
        "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
        Category.PERFORMANCE,
        6,
        Severity.WARNING,
        new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final int DEFAULT_MAX_DEPTH = 10;

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getParentNode() instanceof Document) {
            int maxDepth = computeMaxDepth(element, 1);
            int limit = getMaxDepth();
            if (maxDepth > limit) {
                context.report(ISSUE, element, context.getLocation(element),
                    "Layout hierarchy is too deep: " + maxDepth + " levels (max allowed is " + limit + ")");
            }
        }
    }

    private static int computeMaxDepth(Node node, int currentDepth) {
        int max = currentDepth;
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                int childDepth = computeMaxDepth(child, currentDepth + 1);
                if (childDepth > max) {
                    max = childDepth;
                }
            }
        }
        return max;
    }

    private static int getMaxDepth() {
        String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_DEPTH;
    }
}