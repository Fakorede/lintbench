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

    public static final Issue TOO_DEEP = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter " +
            "layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth " +
            "is 10 but can be configured with the environment variable " +
            "`ANDROID_LINT_MAX_DEPTH`.",
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
            "compound drawables or other tricks for reducing the number of views in this " +
            "layout. The default maximum view count is 80 but can be configured with the " +
            "environment variable `ANDROID_LINT_MAX_VIEWS`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private int mViewCount;
    private int mMaxViews;
    private int mMaxDepth;
    private boolean mTooManyReported;
    private boolean mTooDeepReported;

    public TooManyViewsDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mViewCount = 0;
        mTooManyReported = false;
        mTooDeepReported = false;

        String maxDepthStr = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthStr != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepthStr);
            } catch (NumberFormatException e) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
            }
        } else {
            mMaxDepth = DEFAULT_MAX_DEPTH;
        }

        String maxViewsStr = System.getenv("ANDROID_LINT_MAX_VIEWS");
        if (maxViewsStr != null) {
            try {
                mMaxViews = Integer.parseInt(maxViewsStr);
            } catch (NumberFormatException e) {
                mMaxViews = DEFAULT_MAX_VIEWS;
            }
        } else {
            mMaxViews = DEFAULT_MAX_VIEWS;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;

        if (!mTooManyReported && mViewCount > mMaxViews) {
            mTooManyReported = true;
            context.report(TOO_MANY, element, context.getLocation(element),
                    "Too many views: The file has " + mViewCount + " views, above the limit" +
                    " of " + mMaxViews + "; if many views end up in the same canvas it will" +
                    " hurt performance. Consider using compound drawables or other tricks" +
                    " for reducing the number of views in this layout.");
        }

        if (!mTooDeepReported) {
            int depth = getDepth(element);
            if (depth > mMaxDepth) {
                mTooDeepReported = true;
                context.report(TOO_DEEP, element, context.getLocation(element),
                        "Layout hierarchy is too deep: The current depth is " + depth +
                        ", which exceeds the maximum of " + mMaxDepth + ". Flatter layouts" +
                        " perform better.");
            }
        }
    }

    private int getDepth(@NonNull Element element) {
        int depth = 0;
        org.w3c.dom.Node current = element;
        while (current != null) {
            if (current.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                depth++;
            }
            current = current.getParentNode();
        }
        // subtract 1 for the document node contribution if needed,
        // but since we only count ELEMENT_NODEs this should be correct already.
        // The root element itself counts as depth 1.
        return depth;
    }
}