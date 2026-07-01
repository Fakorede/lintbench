package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private int mViewCount;

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured with the "
                            + "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            TooManyViewsDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckFile(@NonNull Context context) {
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
        if (element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
            int max = getMaxViewCount();
            if (mViewCount > max) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format("This layout has too many views: %d views, it should have <= %d!",
                                mViewCount, max));
            }
        }
    }

    private static int getMaxViewCount() {
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Ignore invalid environment variable values
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}