package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider "
                            + "using compound drawables or other tricks for reducing the number "
                            + "of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured with "
                            + "the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    private int mViewCount;
    private boolean mAlreadyReported;
    private int mMaxViewCount;

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mViewCount = 0;
        mAlreadyReported = false;
        mMaxViewCount = DEFAULT_MAX_VIEW_COUNT;
        String maxViewCountEnv = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (maxViewCountEnv != null) {
            try {
                mMaxViewCount = Integer.parseInt(maxViewCountEnv);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
        if (!mAlreadyReported && mViewCount > mMaxViewCount) {
            mAlreadyReported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Too many views in this layout: "
                            + mViewCount
                            + " views (limit is "
                            + mMaxViewCount
                            + "). Consider using compound drawables or other tricks for "
                            + "reducing the number of views in this layout.");
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        // Nothing to do here; state is tracked in visitElement
    }
}