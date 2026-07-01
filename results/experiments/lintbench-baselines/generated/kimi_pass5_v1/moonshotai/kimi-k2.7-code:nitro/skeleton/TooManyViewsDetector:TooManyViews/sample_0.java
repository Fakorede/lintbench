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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_VIEWS = 80;
    private static final String MAX_VIEW_COUNT_ENV = "ANDROID_LINT_MAX_VIEW_COUNT";

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing the number of views in this layout. "
                            + "The maximum view count defaults to "
                            + DEFAULT_MAX_VIEWS
                            + " but can be configured with the environment variable `"
                            + MAX_VIEW_COUNT_ENV
                            + "`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mViewCount;
    private int mMaxViews;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
        mMaxViews = getMaxViews(context);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        if (mViewCount > mMaxViews && element == element.getOwnerDocument().getDocumentElement()) {
            String message = String.format(
                    "Layout has too many views (%1$d, maximum %2$d)",
                    mViewCount,
                    mMaxViews);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static int getMaxViews(@NonNull Context context) {
        String value = System.getenv(MAX_VIEW_COUNT_ENV);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // fall back to default
            }
        }
        return DEFAULT_MAX_VIEWS;
    }
}