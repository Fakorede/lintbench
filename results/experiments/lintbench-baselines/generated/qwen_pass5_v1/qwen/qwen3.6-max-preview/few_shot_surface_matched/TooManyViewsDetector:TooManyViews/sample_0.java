package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Too many views in a layout",
            "Using too many views in a single layout is bad for performance. "
                    + "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n"
                    + "The maximum view count defaults to 80 but can be configured with the environment variable ANDROID_LINT_MAX_VIEW_COUNT.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private int mViewCount;

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mViewCount = 0;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.DOCUMENT_NODE) {
            int max = DEFAULT_MAX_VIEW_COUNT;
            String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
            if (env != null) {
                try {
                    max = Integer.parseInt(env);
                } catch (NumberFormatException ignored) {
                }
            }

            if (mViewCount > max) {
                String message = String.format("This layout has too many views: %1$d > %2$d", mViewCount, max);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }
}