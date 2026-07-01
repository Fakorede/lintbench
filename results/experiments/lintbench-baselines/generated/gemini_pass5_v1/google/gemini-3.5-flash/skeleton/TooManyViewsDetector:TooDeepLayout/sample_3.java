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
import java.util.Collections;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mDepth;
    private int mMaxDepth;
    private Element mSubtreeHeader;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
        mMaxDepth = 10;
        String maxDepthStr = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthStr != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepthStr);
            } catch (NumberFormatException e) {
                // Keep default
            }
        }
        mSubtreeHeader = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton(XmlScanner.ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        if (mDepth > mMaxDepth && mSubtreeHeader == null) {
            mSubtreeHeader = element;
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        if (element == mSubtreeHeader) {
            String message = String.format(
                    "Layout hierarchy is too deep (%d); maximum allowed is %d",
                    mDepth, mMaxDepth);
            context.report(ISSUE, element, context.getNameLocation(element), message);
            mSubtreeHeader = null;
        }
        mDepth--;
    }
}