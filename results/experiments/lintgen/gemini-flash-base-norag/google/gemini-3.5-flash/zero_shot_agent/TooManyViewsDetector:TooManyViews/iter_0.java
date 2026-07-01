package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the " +
            "number of views in this layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the " +
            "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    public TooManyViewsDetector() {
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        NodeList elements = document.getElementsByTagName("*");
        int viewCount = elements.getLength();

        int max = DEFAULT_MAX_VIEW_COUNT;
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                max = Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Ignore and use default
            }
        }

        if (viewCount > max) {
            String message = String.format(
                    "This layout has too many views (%1$d over the limit of %2$d)",
                    viewCount, max
            );
            context.report(ISSUE, root, context.getNameLocation(root), message);
        }
    }
}