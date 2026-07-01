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
 * Checks whether a layout has too deep a hierarchy, which can hurt performance.
 */
public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    private static final int DEFAULT_MAX_DEPTH = 10;

    /** Issue: layout hierarchy is too deep */
    public static final Issue TOO_DEEP = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter " +
            "layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is " +
            DEFAULT_MAX_DEPTH + " but can be configured with the environment variable " +
            "`ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            2,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** The current depth while traversing the XML tree */
    private int mDepth;

    /** Whether we've already reported a warning for this file */
    private boolean mAlreadyReported;

    /** The configured maximum depth */
    private int mMaxDepth;

    /** Creates a new {@link TooManyViewsDetector} */
    public TooManyViewsDetector() {
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
        mAlreadyReported = false;
        mMaxDepth = getMaxDepth();
    }

    /**
     * Returns the maximum allowed layout depth. Reads from the environment variable
     * {@code ANDROID_LINT_MAX_DEPTH} if set, otherwise returns the default.
     */
    private static int getMaxDepth() {
        String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (env != null) {
            try {
                int value = Integer.parseInt(env.trim());
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignore) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_DEPTH;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;

        if (!mAlreadyReported && mDepth > mMaxDepth) {
            context.report(
                    TOO_DEEP,
                    element,
                    context.getLocation(element),
                    String.format(
                            "This layout has too many nested views: %1$d levels, maximum recommended is %2$d",
                            mDepth,
                            mMaxDepth));
            mAlreadyReported = true;
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}