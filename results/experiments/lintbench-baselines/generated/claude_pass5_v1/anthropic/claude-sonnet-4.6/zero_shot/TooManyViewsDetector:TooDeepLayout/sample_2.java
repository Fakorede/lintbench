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

/**
 * Checks whether a layout has too deep a hierarchy.
 */
public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_DEPTH = 10;

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

    /** The maximum allowed layout depth */
    private int mMaxDepth;

    /** Current depth during traversal */
    private int mDepth;

    /** Whether we've already reported an error for this file */
    private boolean mAlreadyReported;

    /** Constructs a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mDepth = 0;
        mAlreadyReported = false;

        int maxDepth = DEFAULT_MAX_DEPTH;
        String maxDepthStr = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthStr != null) {
            try {
                maxDepth = Integer.parseInt(maxDepthStr.trim());
            } catch (NumberFormatException e) {
                // use default
            }
        }
        mMaxDepth = maxDepth;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;

        if (!mAlreadyReported && mDepth > mMaxDepth) {
            mAlreadyReported = true;
            context.report(
                    TOO_DEEP,
                    element,
                    context.getLocation(element),
                    String.format(
                            "This layout has too many nested views: %1$d levels, " +
                            "the maximum is %2$d!",
                            mDepth, mMaxDepth));
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}