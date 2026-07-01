package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Collection;

/**
 * Checks whether a layout has too many views or too deep a hierarchy.
 */
public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    private static final int MAX_DEPTH_DEFAULT = 10;
    private static final int MAX_VIEWS_DEFAULT = 80;

    private static final String MAX_DEPTH_ENV_VAR = "ANDROID_LINT_MAX_DEPTH";
    private static final String MAX_VIEWS_ENV_VAR = "ANDROID_LINT_MAX_VIEWS";

    /** Issue: Layout hierarchy is too deep */
    public static final Issue TOO_DEEP = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter " +
            "layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is " +
            MAX_DEPTH_DEFAULT + " but can be configured with the environment variable " +
            "`" + MAX_DEPTH_ENV_VAR + "`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Issue: Layout has too many views */
    public static final Issue TOO_MANY = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this layout. " +
            "The default maximum view count is " + MAX_VIEWS_DEFAULT + " but can be configured " +
            "with the environment variable `" + MAX_VIEWS_ENV_VAR + "`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Current depth in the layout hierarchy */
    private int mDepth;

    /** Number of views in the current layout file */
    private int mViewCount;

    /** Whether we've already warned about too many views in this file */
    private boolean mWarnedAboutViews;

    /** Whether we've already warned about too deep a hierarchy in this file */
    private boolean mWarnedAboutDepth;

    /** Maximum allowed depth */
    private int mMaxDepth = -1;

    /** Maximum allowed view count */
    private int mMaxViews = -1;

    /**
     * Constructs a new {@link TooManyViewsDetector}.
     */
    public TooManyViewsDetector() {
    }

    private int getMaxDepth() {
        if (mMaxDepth == -1) {
            mMaxDepth = MAX_DEPTH_DEFAULT;
            String env = System.getenv(MAX_DEPTH_ENV_VAR);
            if (env != null) {
                try {
                    mMaxDepth = Integer.parseInt(env.trim());
                } catch (NumberFormatException ignore) {
                    // Use default
                }
            }
        }
        return mMaxDepth;
    }

    private int getMaxViews() {
        if (mMaxViews == -1) {
            mMaxViews = MAX_VIEWS_DEFAULT;
            String env = System.getenv(MAX_VIEWS_ENV_VAR);
            if (env != null) {
                try {
                    mMaxViews = Integer.parseInt(env.trim());
                } catch (NumberFormatException ignore) {
                    // Use default
                }
            }
        }
        return mMaxViews;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
        mViewCount = 0;
        mWarnedAboutViews = false;
        mWarnedAboutDepth = false;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
        mDepth++;

        int maxViews = getMaxViews();
        int maxDepth = getMaxDepth();

        if (mViewCount > maxViews && !mWarnedAboutViews) {
            mWarnedAboutViews = true;
            String message = String.format(
                    "Too many views: The file has %1$d views, more than the allowed %2$d " +
                    "(set by environment variable %3$s)",
                    mViewCount, maxViews, MAX_VIEWS_ENV_VAR);
            context.report(TOO_MANY, element, context.getLocation(element), message);
        }

        if (mDepth > maxDepth && !mWarnedAboutDepth) {
            mWarnedAboutDepth = true;
            String message = String.format(
                    "Layout hierarchy is too deep: The file has a depth of %1$d, more than " +
                    "the allowed %2$d (set by environment variable %3$s)",
                    mDepth, maxDepth, MAX_DEPTH_ENV_VAR);
            context.report(TOO_DEEP, element, context.getLocation(element), message);
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}