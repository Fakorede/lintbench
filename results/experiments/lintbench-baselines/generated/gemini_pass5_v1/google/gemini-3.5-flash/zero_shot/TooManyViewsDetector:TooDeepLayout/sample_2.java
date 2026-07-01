package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
            "Layouts with too much nesting is bad for performance. Consider using a "
                    + "flatter layout (such as `RelativeLayout` or `GridLayout`).",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        int maxDepth = 10;
        String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (env != null) {
            try {
                maxDepth = Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Fallback to default of 10
            }
        }

        int depth = getMaxDepth(root);
        if (depth > maxDepth) {
            context.report(
                    TOO_DEEP,
                    root,
                    context.getNameLocation(root),
                    String.format("Layout hierarchy is too deep (%d is greater than %d)", depth, maxDepth)
            );
        }
    }

    private int getMaxDepth(Node node) {
        int max = 0;
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                max = Math.max(max, getMaxDepth(child));
            }
        }
        return max + 1;
    }
}