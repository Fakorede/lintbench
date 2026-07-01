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

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using " +
            "a flatter layout (such as `RelativeLayout` or `GridLayout`). The default " +
            "maximum depth is 10 but can be configured with the environment " +
            "variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        int maxDepthLimit = getMaxDepthLimit();
        int maxDepth = getMaxDepth(root);
        if (maxDepth > maxDepthLimit) {
            Element offendingElement = findElementAtDepth(root, maxDepthLimit + 1, 1);
            if (offendingElement == null) {
                offendingElement = root;
            }
            context.report(
                    ISSUE,
                    offendingElement,
                    context.getNameLocation(offendingElement),
                    String.format("Layout hierarchy is too deep (%d) (limit %d)", maxDepth, maxDepthLimit)
            );
        }
    }

    private static int getMaxDepthLimit() {
        String val = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                // ignore and fallback to default
            }
        }
        return 10;
    }

    private int getMaxDepth(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return 0;
        }
        int maxChildDepth = 0;
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                maxChildDepth = Math.max(maxChildDepth, getMaxDepth(child));
            }
        }
        return maxChildDepth + 1;
    }

    private Element findElementAtDepth(Node node, int targetDepth, int currentDepth) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }
        if (currentDepth == targetDepth) {
            return (Element) node;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            Element found = findElementAtDepth(child, targetDepth, currentDepth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}