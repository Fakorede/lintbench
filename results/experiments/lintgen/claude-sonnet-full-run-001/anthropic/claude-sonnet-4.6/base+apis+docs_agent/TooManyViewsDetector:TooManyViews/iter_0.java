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

    private static final int sMaxViewCount;

    static {
        int maxViewCount = DEFAULT_MAX_VIEW_COUNT;
        String envValue = System.getenv(MAX_VIEW_COUNT_ENV_VAR);
        if (envValue != null) {
            try {
                maxViewCount = Integer.parseInt(envValue.trim());
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        sMaxViewCount = maxViewCount;
    }

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this " +
            "layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the " +
            "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Count of views in the current layout file */
    private int mViewCount;

    /** Whether we've already reported an error for the current file */
    private boolean mAlreadyReported;

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

        if (!mAlreadyReported && mViewCount > sMaxViewCount) {
            mAlreadyReported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Too many views in this layout: %1$d views (limit is %2$d)",
                            mViewCount,
                            sMaxViewCount));
        }
    }
}