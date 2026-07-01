package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class TooManyViewsDetector extends LayoutDetector {

    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";
    private static final int DEFAULT_MAX_DEPTH = 10;

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

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int depth = getDepth(element);
        int maxDepth = getMaxDepth();
        if (depth > maxDepth) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Layout hierarchy is too deep: depth is " + depth + " (max allowed is " + maxDepth + ")");
        }
    }

    private static int getDepth(Element element) {
        int depth = 1;
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            depth++;
            parent = parent.getParentNode();
        }
        return depth;
    }

    private static int getMaxDepth() {
        try {
            String env = System.getenv(ENV_MAX_DEPTH);
            if (env != null) {
                return Integer.parseInt(env);
            }
        } catch (NumberFormatException | SecurityException ignored) {
        }
        return DEFAULT_MAX_DEPTH;
    }
}