package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting are bad for performance. Consider using a "
                            + "flatter layout (such as `RelativeLayout` or `GridLayout`). The "
                            + "default maximum depth is 10 but can be configured with the "
                            + "environment variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mMaxDepth;
    private java.util.Deque<org.w3c.dom.Element> mStack;

    public TooManyViewsDetector() {
        mMaxDepth = DEFAULT_MAX_DEPTH;
    }

    @Override
    public void beforeCheckFile(Context context) {
        String maxDepth = System.getenv(ENV_MAX_DEPTH);
        if (maxDepth != null) {
            try {
                mMaxDepth = Integer.parseInt(maxDepth);
            } catch (NumberFormatException e) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
            }
        } else {
            mMaxDepth = DEFAULT_MAX_DEPTH;
        }
        mStack = new java.util.ArrayDeque<>();
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        mStack.push(element);
        if (mStack.size() > mMaxDepth) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Layout hierarchy is too deep: "
                            + mStack.size()
                            + " levels (maximum "
                            + mMaxDepth
                            + ")");
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, org.w3c.dom.Element element) {
        mStack.pop();
    }
}