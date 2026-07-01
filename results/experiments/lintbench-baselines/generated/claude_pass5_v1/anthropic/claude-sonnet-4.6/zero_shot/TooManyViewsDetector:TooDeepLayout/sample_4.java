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

import java.util.Collection;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final int DEFAULT_MAX_VIEWS = 80;

    private static final String MAX_DEPTH_ENV_VAR = "ANDROID_LINT_MAX_DEPTH";
    private static final String MAX_VIEWS_ENV_VAR = "ANDROID_LINT_MAX_VIEWS";

    public static final Issue TOO_DEEP = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter " +
            "layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is " +
            "10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    public static final Issue TOO_MANY = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using " +
            "compound drawables or other tricks for reducing the number of views in this layout. " +
            "The default maximum view count is 80 but can be configured with the environment " +
            "variable `ANDROID_LINT_MAX_VIEWS`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private int mViewCount;
    private int mMaxDepth;
    private int mMaxViews;
    private int mCurrentDepth;
    private boolean mTooManyReported;
    private boolean mTooDeepReported;

    public TooManyViewsDetector() {
    }

    private int getMaxDepth() {
        String env = System.getenv(MAX_DEPTH_ENV_VAR);
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignore) {
            }
        }
        return DEFAULT_MAX_DEPTH;
    }

    private int getMaxViews() {
        String env = System.getenv(MAX_VIEWS_ENV_VAR);
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignore) {
            }
        }
        return DEFAULT_MAX_VIEWS;
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mViewCount = 0;
        mCurrentDepth = 0;
        mTooManyReported = false;
        mTooDeepReported = false;
        mMaxDepth = getMaxDepth();
        mMaxViews = getMaxViews();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
        mCurrentDepth++;

        if (!mTooManyReported && mViewCount > mMaxViews) {
            mTooManyReported = true;
            context.report(TOO_MANY, element, context.getLocation(element),
                    String.format("Too many views: The file has %1$d views, above the limit of %2$d",
                            mViewCount, mMaxViews));
        }

        if (!mTooDeepReported && mCurrentDepth > mMaxDepth) {
            mTooDeepReported = true;
            context.report(TOO_DEEP, element, context.getLocation(element),
                    String.format("Layout hierarchy is too deep: The file has a depth of %1$d, " +
                            "above the limit of %2$d", mCurrentDepth, mMaxDepth));
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mCurrentDepth--;
    }
}