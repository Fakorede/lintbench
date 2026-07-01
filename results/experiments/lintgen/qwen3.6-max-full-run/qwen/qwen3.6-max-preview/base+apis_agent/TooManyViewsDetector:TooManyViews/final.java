package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        int max = getMaxViewCount();
        int count = countElements(document.getDocumentElement());

        if (count > max) {
            String message = String.format("This layout has too many views (%1$d views, max is %2$d)", count, max);
            context.report(ISSUE, context.getLocation(document.getDocumentElement()), message);
        }
    }

    private int getMaxViewCount() {
        String env = System.getenv(ENV_MAX_VIEW_COUNT);
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }

    private int countElements(Node node) {
        int count = 0;
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            count++;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            count += countElements(child);
        }
        return count;
    }
}