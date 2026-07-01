package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

public class TooManyViewsDetector extends LayoutDetector {

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
    private boolean mSubtreeExceeded;

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mDepth = 0;
        mSubtreeExceeded = false;

        String maxDepthStr = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthStr != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepthStr);
            } catch (NumberFormatException e) {
                mMaxDepth = 10;
            }
        } else {
            mMaxDepth = 10;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(XmlScanner.ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        if (mDepth > mMaxDepth) {
            if (!mSubtreeExceeded) {
                mSubtreeExceeded = true;
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Layout hierarchy is too deep (" + mDepth + " > " + mMaxDepth + ")");
            }
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
        if (mDepth < mMaxDepth) {
            mSubtreeExceeded = false;
        }
    }
}