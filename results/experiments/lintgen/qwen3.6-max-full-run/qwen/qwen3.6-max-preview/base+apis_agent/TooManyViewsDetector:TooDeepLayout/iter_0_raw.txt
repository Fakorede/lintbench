package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance.  Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment  variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static int sMaxDepth = -1;

    private static int getMaxDepth() {
        if (sMaxDepth != -1) {
            return sMaxDepth;
        }
        String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (env != null) {
            try {
                sMaxDepth = Integer.parseInt(env);
                return sMaxDepth;
            } catch (NumberFormatException ignored) {
            }
        }
        sMaxDepth = 10;
        return sMaxDepth;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            return;
        }

        int maxDepth = getMaxDepth();
        checkDepth(context, element, 1, maxDepth);
    }

    private void checkDepth(XmlContext context, Element element, int depth, int maxDepth) {
        if (depth > maxDepth) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Layout hierarchy is too deep: depth is " + depth + " (max is " + maxDepth + ")");
            return;
        }

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkDepth(context, (Element) child, depth + 1, maxDepth);
            }
            child = child.getNextSibling();
        }
    }
}