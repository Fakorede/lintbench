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
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance. Consider using a flatter "
                            + "layout (such as RelativeLayout or GridLayout). The default maximum depth "
                            + "is 10 but can be configured with the environment variable ANDROID_LINT_MAX_DEPTH.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mDepth = 0;
    private int mMaxDepth = 10;
    private boolean mReported = false;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDepth = 0;
        mReported = false;
        String maxDepthEnv = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthEnv != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepthEnv);
            } catch (NumberFormatException e) {
                mMaxDepth = 10;
            }
        } else {
            mMaxDepth = 10;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        if (!mReported && mDepth > mMaxDepth) {
            mReported = true;
            context.report(ISSUE, element, context.getLocation(element),
                    "Layout hierarchy is too deep");
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}