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

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance.  "
                            + "Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). "
                            + "The default maximum depth is 10 but can be configured with the environment  "
                            + "variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mMaxDepth;
    private int mCurrentDepth;
    private boolean mReported;

    @Override
    public void beforeCheckFile(Context context) {
        mCurrentDepth = 0;
        mReported = false;

        int max = 10;
        String val = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (val != null) {
            try {
                max = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        mMaxDepth = max;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mCurrentDepth++;
        if (mCurrentDepth > mMaxDepth) {
            if (!mReported) {
                mReported = true;
                String message = String.format(
                        "Layout hierarchy is too deep (%d > %d)",
                        mCurrentDepth, mMaxDepth);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        mCurrentDepth--;
    }
}