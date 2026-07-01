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
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector {
    private static final int DEFAULT_MAX_DEPTH = 10;

    public static final Issue ISSUE = Issue.create(
        "TooDeepLayout",
        "Layout hierarchy is too deep",
        "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as RelativeLayout or GridLayout). The default maximum depth is 10 but can be configured with the environment variable ANDROID_LINT_MAX_DEPTH.",
        Category.PERFORMANCE,
        1,
        Severity.WARNING,
        new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getClientProperty("reported") != null) {
            return;
        }

        int depth = getDepth(element);
        int maxDepth = getMaxDepth();

        if (depth > maxDepth) {
            context.putClientProperty("reported", true);
            Element root = context.getDocument().getDocumentElement();
            context.report(ISSUE, root, context.getLocation(root),
                "Layout hierarchy is too deep: " + depth + " levels (max allowed is " + maxDepth + ")");
        }
    }

    private static int getDepth(Element element) {
        int depth = 0;
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                depth++;
            }
            parent = parent.getParentNode();
        }
        return depth;
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