package com.android.tools.lint.checks;

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

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String MAX_DEPTH_ENV = "ANDROID_LINT_MAX_DEPTH";

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting are bad for performance. Consider using a flatter "
                            + "layout (such as `RelativeLayout` or `GridLayout`). The default maximum "
                            + "depth is 10 but can be configured with the environment variable "
                            + "`ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mDepth;
    private int mMaxDepth;
    private boolean mReported;

    @Override
    public void beforeCheckFile(Context context) {
        mDepth = 0;
        mReported = false;

        String maxDepth = System.getenv(MAX_DEPTH_ENV);
        if (maxDepth != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepth);
            } catch (NumberFormatException e) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
            }
        } else {
            mMaxDepth = DEFAULT_MAX_DEPTH;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mDepth++;
        if (mDepth > mMaxDepth && !mReported) {
            mReported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Layout hierarchy is too deep with a depth of %1$d (maximum recommended: %2$d)",
                            mDepth, mMaxDepth));
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        mDepth--;
    }
}