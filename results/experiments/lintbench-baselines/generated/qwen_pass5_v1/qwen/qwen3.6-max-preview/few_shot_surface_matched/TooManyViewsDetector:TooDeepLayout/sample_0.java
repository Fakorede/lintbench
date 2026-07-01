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

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private int mDepth;
    private int mMaxDepth;

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout " +
            "(such as RelativeLayout or GridLayout). The default maximum depth is 10 but can be " +
            "configured with the environment variable ANDROID_LINT_MAX_DEPTH.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mDepth = 0;
        String maxDepthEnv = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthEnv != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepthEnv);
            } catch (NumberFormatException e) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
            }
        } else {
            mMaxDepth = DEFAULT_MAX_DEPTH;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        if (mDepth > mMaxDepth) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Layout hierarchy is too deep: depth is %1$d (max is %2$d)", mDepth, mMaxDepth);
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}