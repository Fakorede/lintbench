package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String MAX_DEPTH_ENV = "ANDROID_LINT_MAX_DEPTH";

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting are bad for performance. "
                            + "Consider using a flatter layout (such as `RelativeLayout` or "
                            + "`GridLayout`). The default maximum depth is 10 but can be "
                            + "configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Deque<Element> mStack = new ArrayDeque<>();
    private int mMaxDepth = DEFAULT_MAX_DEPTH;
    private int mDeepestDepth;
    private Element mDeepestElement;

    @Override
    public void beforeCheckFile(Context context) {
        mStack.clear();
        mDeepestDepth = 0;
        mDeepestElement = null;
        mMaxDepth = DEFAULT_MAX_DEPTH;

        String maxDepth = System.getenv(MAX_DEPTH_ENV);
        if (maxDepth != null) {
            try {
                int value = Integer.parseInt(maxDepth);
                if (value > 0) {
                    mMaxDepth = value;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return LayoutDetector.getLayoutTags();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mStack.push(element);
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        int depth = mStack.size();
        if (depth > mMaxDepth && depth > mDeepestDepth) {
            mDeepestDepth = depth;
            mDeepestElement = element;
        }
        mStack.pop();
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mDeepestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            Location location = xmlContext.getLocation(mDeepestElement);
            String message = String.format(
                    "Layout hierarchy is too deep: %1$d levels deep (maximum recommended is %2$d)",
                    mDeepestDepth, mMaxDepth);
            xmlContext.report(ISSUE, mDeepestElement, location, message);
        }
    }
}