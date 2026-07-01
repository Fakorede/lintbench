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

    /** The maximum depth allowed */
    private int mMaxDepth;

    /** Current depth */
    private int mDepth;

    /** Whether we've already warned about this file */
    private boolean mAlreadyWarned;

    /** Constructs a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    private int getMaxDepth() {
        String maxDepthStr = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthStr != null) {
            try {
                return Integer.parseInt(maxDepthStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        return DEFAULT_MAX_DEPTH;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;

        if (mDepth == 1) {
            // Reset state for each new file/root element
            mAlreadyWarned = false;
            mMaxDepth = getMaxDepth();
        }

        if (!mAlreadyWarned && mDepth > mMaxDepth) {
            mAlreadyWarned = true;
            context.report(
                    TOO_DEEP,
                    element,
                    context.getLocation(element),
                    String.format(
                            "This layout has too many nested views: %1$d levels, " +
                            "maximum recommended is %2$d!",
                            mDepth,
                            mMaxDepth));
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}