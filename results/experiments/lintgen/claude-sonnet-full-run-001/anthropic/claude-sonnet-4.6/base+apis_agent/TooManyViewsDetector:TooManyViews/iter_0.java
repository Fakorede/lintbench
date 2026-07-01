package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;

/**
 * Checks whether a layout has too many views.
 */
public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    private static final String MAX_VIEW_COUNT_ENV_VAR = "ANDROID_LINT_MAX_VIEW_COUNT";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this layout.\n" +
            "\n" +
            "The maximum view count defaults to 80 but can be configured with the " +
            "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private int mViewCount;
    private boolean mAlreadyReported;
    private int mMaxViewCount = -1;

    /** Constructs a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    private int getMaxViewCount() {
        if (mMaxViewCount == -1) {
            mMaxViewCount = DEFAULT_MAX_VIEW_COUNT;
            String env = System.getenv(MAX_VIEW_COUNT_ENV_VAR);
            if (env != null) {
                try {
                    int value = Integer.parseInt(env.trim());
                    if (value > 0) {
                        mMaxViewCount = value;
                    }
                } catch (NumberFormatException e) {
                    // Use default
                }
            }
        }
        return mMaxViewCount;
    }

    @Override
    public void beforeCheckFile(Context context) {
        mViewCount = 0;
        mAlreadyReported = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mViewCount++;

        if (!mAlreadyReported && mViewCount > getMaxViewCount()) {
            mAlreadyReported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Too many views in this layout: %1$d views (limit is %2$d)",
                            mViewCount,
                            getMaxViewCount()));
        }
    }
}