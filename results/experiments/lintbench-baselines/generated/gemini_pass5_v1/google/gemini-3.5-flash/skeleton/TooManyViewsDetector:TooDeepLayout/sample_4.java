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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance.  Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment  variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mDepth;
    private int mMaxDepth = -1;
    private boolean mSubtreeExplored;

    private int getMaxDepth() {
        if (mMaxDepth == -1) {
            mMaxDepth = 10;
            String val = System.getenv("ANDROID_LINT_MAX_DEPTH");
            if (val != null) {
                try {
                    mMaxDepth = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        return mMaxDepth;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
        mMaxDepth = -1;
        mSubtreeExplored = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        int maxDepth = getMaxDepth();
        mDepth++;
        if (mDepth > maxDepth) {
            if (!mSubtreeExplored) {
                mSubtreeExplored = true;
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        String.format("Layout hierarchy is too deep (%1$d > %2$d): "
                                + "Layouts with too much nesting is bad for performance. "
                                + "Consider using a flatter layout (such as RelativeLayout "
                                + "or GridLayout).", mDepth, maxDepth));
            }
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
        if (mDepth < getMaxDepth()) {
            mSubtreeExplored = false;
        }
    }
}