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
    private static final String ENV_VAR_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";

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

    private int mDepth;
    private int mMaxDepth;
    private boolean mAlreadyReported;

    public TooManyViewsDetector() {
    }

    private int getMaxDepth() {
        String env = System.getenv(ENV_VAR_MAX_DEPTH);
        if (env != null) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException ignore) {
            }
        }
        return DEFAULT_MAX_DEPTH;
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mDepth = 0;
        mMaxDepth = getMaxDepth();
        mAlreadyReported = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        if (mDepth > mMaxDepth && !mAlreadyReported) {
            mAlreadyReported = true;
            context.report(
                    TOO_DEEP,
                    element,
                    context.getLocation(element),
                    String.format(
                            "This layout has too many nested layouts: %1$d levels, " +
                            "whereas the maximum recommended depth is %2$d",
                            mDepth, mMaxDepth));
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
        if (mDepth <= mMaxDepth) {
            mAlreadyReported = false;
        }
    }
}