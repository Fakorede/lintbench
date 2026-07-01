package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEWS = 80;

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. "
                    + "Consider using compound drawables or other tricks for reducing the number of views in this layout.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String folder = context.file.getParentFile().getName();
        if (!folder.startsWith("layout")) {
            return;
        }

        int count = countElements(root);
        int max = getMaxViews();
        if (count > max) {
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "Layout has too many views: " + count + " (max " + max + ")"
            );
        }
    }

    private static int countElements(Element element) {
        int count = 1;
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count += countElements((Element) child);
            }
            child = child.getNextSibling();
        }
        return count;
    }

    private static int getMaxViews() {
        String value = System.getenv(ENV_MAX_VIEW_COUNT);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_VIEWS;
    }
}