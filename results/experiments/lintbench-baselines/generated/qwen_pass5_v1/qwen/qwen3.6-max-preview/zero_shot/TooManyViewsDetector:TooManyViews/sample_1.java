package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends LayoutDetector {

    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static int getMaxViewCount() {
        String value = System.getenv(ENV_MAX_VIEW_COUNT);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        int count = countViews(root);
        int max = getMaxViewCount();
        if (count > max) {
            String message = String.format(
                    "This layout has too many views (%1$d views, max is %2$d); consider using compound drawables or other tricks for reducing the number of views in this layout.",
                    count, max);
            context.report(ISSUE, root, context.getLocation(root), message);
        }
    }

    private static int countViews(Node node) {
        int count = 0;
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String tag = node.getLocalName();
            if (tag != null && !tag.equals("merge") && !tag.equals("include") &&
                !tag.equals("requestFocus") && !tag.equals("tag")) {
                count = 1;
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            count += countViews(children.item(i));
        }
        return count;
    }
}