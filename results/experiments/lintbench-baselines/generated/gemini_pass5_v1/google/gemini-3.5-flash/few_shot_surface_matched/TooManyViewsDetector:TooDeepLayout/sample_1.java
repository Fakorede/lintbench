package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance.  Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment  variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            TooManyViewsDetector.class, Scope.LAYOUT_SCOPE));

    private int mDepth;
    private int mMaxDepth = 10;
    private boolean mReported;

    public TooManyViewsDetector() {
        String val = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (val != null) {
            try {
                mMaxDepth = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                // fallback to default
            }
        }
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mDepth = 0;
        mReported = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mDepth++;
        if (mDepth > mMaxDepth && !mReported) {
            mReported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Layout hierarchy is too deep; depth is " + mDepth + ", maximum allowed is " + mMaxDepth);
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        mDepth--;
    }
}