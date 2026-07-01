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
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting are bad for performance. "
                            + "Consider using a flatter layout (such as `RelativeLayout` or "
                            + "`GridLayout`). The default maximum depth is 10 but can be "
                            + "configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mMaxDepth = DEFAULT_MAX_DEPTH;
    private int mCurrentDepth;
    private int mDeepestDepth;
    private Element mDeepestElement;
    private Element mRoot;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCurrentDepth = 0;
        mDeepestDepth = 0;
        mDeepestElement = null;
        mRoot = null;

        String override = System.getenv(ENV_MAX_DEPTH);
        if (override != null) {
            try {
                mMaxDepth = Integer.parseInt(override);
            } catch (NumberFormatException e) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mCurrentDepth++;

        if (mCurrentDepth > mDeepestDepth) {
            mDeepestDepth = mCurrentDepth;
            mDeepestElement = element;
        }

        if (mRoot == null) {
            mRoot = element;
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mCurrentDepth--;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mDeepestDepth > mMaxDepth && context instanceof XmlContext && mRoot != null) {
            XmlContext xmlContext = (XmlContext) context;
            String message = String.format(
                    "Layout hierarchy is too deep (depth = %d, maximum recommended is %d)",
                    mDeepestDepth, mMaxDepth);
            xmlContext.report(ISSUE, mRoot, xmlContext.getLocation(mRoot), message);
        }
    }
}