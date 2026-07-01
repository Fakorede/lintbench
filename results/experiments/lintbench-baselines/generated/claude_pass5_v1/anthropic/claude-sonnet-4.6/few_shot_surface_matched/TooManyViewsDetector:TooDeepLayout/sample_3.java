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

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance. Consider using a"
                            + " flatter layout (such as `RelativeLayout` or `GridLayout`)."
                            + " The default maximum depth is 10 but can be configured with the"
                            + " environment variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    2,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX_DEPTH = 10;

    private int mDepth;
    private int mMaxDepth;

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mDepth = 0;
        mMaxDepth = DEFAULT_MAX_DEPTH;
        String maxDepthStr = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthStr != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepthStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mDepth++;
        if (mDepth == mMaxDepth + 1) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This layout has too many nested layouts: "
                            + mDepth
                            + " levels, and the maximum recommended is "
                            + mMaxDepth);
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        mDepth--;
    }
}