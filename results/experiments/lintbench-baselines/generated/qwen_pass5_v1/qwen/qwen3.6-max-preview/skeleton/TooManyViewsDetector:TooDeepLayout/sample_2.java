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
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as RelativeLayout or GridLayout). The default maximum depth is 10 but can be configured with the environment variable ANDROID_LINT_MAX_DEPTH.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mDepth = 0;
    private int mMaxDepth = 10;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
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
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        if (mDepth == mMaxDepth + 1) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Layout hierarchy is too deep: " + mDepth + " levels (max is " + mMaxDepth + ")");
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}