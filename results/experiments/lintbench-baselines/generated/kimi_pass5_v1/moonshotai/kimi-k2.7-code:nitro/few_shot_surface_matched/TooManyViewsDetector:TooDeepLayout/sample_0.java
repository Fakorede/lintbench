package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String MAX_DEPTH_ENV = "ANDROID_LINT_MAX_DEPTH";

    private int mMaxDepth = DEFAULT_MAX_DEPTH;
    private int mDepth;
    private boolean mReported;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
        mReported = false;
        String maxDepth = System.getenv(MAX_DEPTH_ENV);
        if (maxDepth != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepth);
            } catch (NumberFormatException e) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
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
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Layout hierarchy is too deep");
            mReported = true;
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}