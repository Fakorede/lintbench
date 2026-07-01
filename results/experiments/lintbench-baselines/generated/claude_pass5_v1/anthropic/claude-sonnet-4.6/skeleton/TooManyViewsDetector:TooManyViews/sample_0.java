package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private static final String ENV_VAR = "ANDROID_LINT_MAX_VIEW_COUNT";

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider using "
                            + "compound drawables or other tricks for reducing the number of views in this "
                            + "layout.\n\nThe maximum view count defaults to 80 but can be configured with the "
                            + "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mViewCount;
    private boolean mAlreadyReported;

    private int getMaxViewCount() {
        String env = System.getenv(ENV_VAR);
        if (env != null) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException e) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
        mAlreadyReported = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
        if (!mAlreadyReported && mViewCount > getMaxViewCount()) {
            mAlreadyReported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Too many views in this layout: "
                            + mViewCount
                            + " (limit is "
                            + getMaxViewCount()
                            + ")");
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        // Nothing to do here
    }
}