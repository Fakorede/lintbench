package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance. Consider using a flatter"
                            + " layout (such as `RelativeLayout` or `GridLayout`). The default"
                            + " maximum depth is 10 but can be configured with the environment"
                            + " variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mMaxDepth;
    private int mDepth;
    private boolean mReported;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
        mReported = false;
        mMaxDepth = DEFAULT_MAX_DEPTH;

        String envMaxDepth = System.getenv(ENV_MAX_DEPTH);
        if (envMaxDepth != null && !envMaxDepth.isEmpty()) {
            try {
                mMaxDepth = Integer.parseInt(envMaxDepth);
            } catch (NumberFormatException e) {
                // ignore invalid value and keep the default
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        if (!mReported && mDepth > mMaxDepth) {
            String message =
                    String.format(
                            "Layout hierarchy is too deep. [Depth=%d, Threshold=%d]",
                            mDepth, mMaxDepth);
            context.report(ISSUE, element, context.getLocation(element), message);
            mReported = true;
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}