package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing "
                            + "the number of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured "
                            + "with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mViewCount;
    private Location mRootLocation;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
        mRootLocation = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
        if (mRootLocation == null) {
            mRootLocation = context.getLocation(element);
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        // No-op
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        int max = 80;
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                max = Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Ignore and use default
            }
        }
        if (mViewCount > max && mRootLocation != null) {
            String message = String.format(
                    "Layout has too many views (%d > %d)", mViewCount, max);
            context.report(ISSUE, mRootLocation, message);
        }
    }
}