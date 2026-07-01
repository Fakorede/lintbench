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
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.LAYOUT_SCOPE
            )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            int maxDepth = getMaxDepth();
            checkDepth(context, root, 1, maxDepth);
        }
    }

    private void checkDepth(XmlContext context, Element element, int depth, int maxDepth) {
        if (depth > maxDepth) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Layout hierarchy is too deep (%d > %d)", depth, maxDepth)
            );
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkDepth(context, (Element) child, depth + 1, maxDepth);
            }
        }
    }

    private int getMaxDepth() {
        String val = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                // Return default
            }
        }
        return 10;
    }
}