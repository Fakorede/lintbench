package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String MAX_DEPTH_ENV = "ANDROID_LINT_MAX_DEPTH";

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting are bad for performance. Consider using a flatter layout such as RelativeLayout or GridLayout. The default maximum depth is 10, but it can be configured with the ANDROID_LINT_MAX_DEPTH environment variable.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mDepth;
    private boolean mReported;
    private int mMaxDepth = -1;

    @Override
    public void beforeCheckFile(Context context) {
        mDepth = 0;
        mReported = false;
        if (mMaxDepth < 0) {
            mMaxDepth = getMaxDepth();
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScannerConstants.ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mDepth++;
        if (!mReported && mDepth > mMaxDepth) {
            mReported = true;
            String message = String.format(
                    "Layout hierarchy is too deep (depth %1$d exceeds limit of %2$d). "
                            + "Consider using a flatter layout such as RelativeLayout or GridLayout.",
                    mDepth, mMaxDepth);
            context.report(ISSUE, context.getElementLocation(element), message);
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (mDepth > 0) {
            mDepth--;
        }
    }

    private int getMaxDepth() {
        String maxDepthEnv = System.getenv(MAX_DEPTH_ENV);
        if (maxDepthEnv != null) {
            try {
                return Integer.parseInt(maxDepthEnv);
            } catch (NumberFormatException e) {
                // fall back to default
            }
        }
        return DEFAULT_MAX_DEPTH;
    }
}