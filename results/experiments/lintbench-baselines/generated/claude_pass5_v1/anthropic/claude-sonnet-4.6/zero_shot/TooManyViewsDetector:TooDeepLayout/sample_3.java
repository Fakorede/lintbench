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
            "compound drawables or other tricks for reducing the number of views in the layout. " +
            "The default maximum view count is 80 but can be configured with the environment " +
            "variable `ANDROID_LINT_MAX_VIEWS`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private int mViewCount;
    private int mDepth;
    private boolean mTooManyReported;
    private boolean mTooDeepReported;

    private int getMaxDepth() {
        String env = System.getenv(MAX_DEPTH_ENV_VAR);
        if (env != null) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return DEFAULT_MAX_DEPTH;
    }

    private int getMaxViews() {
        String env = System.getenv(MAX_VIEWS_ENV_VAR);
        if (env != null) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return DEFAULT_MAX_VIEWS;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
        mDepth++;

        int maxViews = getMaxViews();
        int maxDepth = getMaxDepth();

        if (mViewCount > maxViews && !mTooManyReported) {
            mTooManyReported = true;
            context.report(TOO_MANY, element, context.getLocation(element),
                    "Too many views: The layout has too many views, already more than " +
                    maxViews + "; consider creating a custom view or using " +
                    "includes");
        }

        if (mDepth > maxDepth && !mTooDeepReported) {
            mTooDeepReported = true;
            context.report(TOO_DEEP, element, context.getLocation(element),
                    "Layout hierarchy is too deep: The layout already has a depth of " +
                    maxDepth + "; consider using a flatter layout (such as `RelativeLayout` " +
                    "or `GridLayout`)");
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mViewCount = 0;
        mDepth = 0;
        mTooManyReported = false;
        mTooDeepReported = false;
    }
}