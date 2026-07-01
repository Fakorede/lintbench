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
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends LayoutDetector {

    public static final Issue TOO_DEEP = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        int maxDepth = getMaxDepth(root);
        int limit = getMaxDepthLimit();
        if (maxDepth > limit) {
            String message = String.format(
                    "Layout hierarchy is too deep (%d levels, limit is %d)",
                    maxDepth, limit);
            context.report(TOO_DEEP, root, context.getNameLocation(root), message);
        }
    }

    private int getMaxDepth(Node node) {
        int maxDepth = 0;
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                maxDepth = Math.max(maxDepth, getMaxDepth(child));
            }
        }
        return maxDepth + 1;
    }

    private int getMaxDepthLimit() {
        String value = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 10;
    }
}