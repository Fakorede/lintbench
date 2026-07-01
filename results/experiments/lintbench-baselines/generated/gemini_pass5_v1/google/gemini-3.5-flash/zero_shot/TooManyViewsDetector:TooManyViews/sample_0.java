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
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the " +
            "number of views in this layout.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        int max = getMaxViewCount();
        int count = countViews(root);
        if (count > max) {
            String message = String.format(
                    "This layout has too many views (%d; limit %d)",
                    count, max
            );
            context.report(ISSUE, root, context.getNameLocation(root), message);
        }
    }

    private static int countViews(Node node) {
        int count = 0;
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            count++;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            count += countViews(children.item(i));
        }
        return count;
    }

    private static int getMaxViewCount() {
        String value = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore and use default
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}