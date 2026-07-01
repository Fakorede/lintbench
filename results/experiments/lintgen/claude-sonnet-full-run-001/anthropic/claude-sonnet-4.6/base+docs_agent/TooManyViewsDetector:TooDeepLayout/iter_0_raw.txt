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

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final int DEFAULT_MAX_COUNT = 80;

    /** The main issue discovered by this detector */
    public static final Issue TOO_DEEP = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter " +
            "layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is " +
            DEFAULT_MAX_DEPTH + " but can be configured with the environment variable " +
            "`ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Issue for too many views */
    public static final Issue TOO_MANY = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this layout. " +
            "The default maximum view count is " + DEFAULT_MAX_COUNT + " but can be configured " +
            "with the environment variable `ANDROID_LINT_MAX_VIEWS`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private int mDepth;
    private int mCount;
    private boolean mTooDeepReported;
    private boolean mTooManyReported;
    private int mMaxDepth;
    private int mMaxCount;

    /** Constructs a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
        mCount = 0;
        mTooDeepReported = false;
        mTooManyReported = false;
        mMaxDepth = getMaxDepth();
        mMaxCount = getMaxCount();
    }

    private static int getMaxDepth() {
        String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (env != null) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return DEFAULT_MAX_DEPTH;
    }

    private static int getMaxCount() {
        String env = System.getenv("ANDROID_LINT_MAX_VIEWS");
        if (env != null) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return DEFAULT_MAX_COUNT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        mCount++;

        if (!mTooManyReported && mCount > mMaxCount) {
            context.report(TOO_MANY, element, context.getLocation(element),
                    "Too many views: The file has " + mCount + " views, more than the maximum " +
                    "of " + mMaxCount + " views allowed per layout.");
            mTooManyReported = true;
        }

        if (!mTooDeepReported && mDepth > mMaxDepth) {
            context.report(TOO_DEEP, element, context.getLocation(element),
                    "Too deep layout: The file has a depth of " + mDepth + ", which exceeds " +
                    "the maximum of " + mMaxDepth + " allowed.");
            mTooDeepReported = true;
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}