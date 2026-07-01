package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
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
                    "Layouts with too much nesting are bad for performance. Layouts that are too deeply nested can be slow to inflate and may cause StackOverflowErrors. Consider using a flatter layout such as RelativeLayout, GridLayout, or ConstraintLayout. The maximum depth can be configured with the ANDROID_LINT_MAX_DEPTH environment variable.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mMaxDepth = DEFAULT_MAX_DEPTH;
    private int mCurrentDepth;
    private int mMaxDepthFound;
    private Location mRootLocation;

    @Override
    public void beforeCheckFile(Context context) {
        mCurrentDepth = 0;
        mMaxDepthFound = 0;
        mRootLocation = null;

        String max = System.getenv(MAX_DEPTH_ENV);
        if (max != null) {
            try {
                mMaxDepth = Integer.parseInt(max);
            } catch (NumberFormatException e) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
            }
        } else {
            mMaxDepth = DEFAULT_MAX_DEPTH;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mCurrentDepth++;

        if (mCurrentDepth > mMaxDepthFound) {
            mMaxDepthFound = mCurrentDepth;
        }

        if (mCurrentDepth == 1) {
            mRootLocation = context.getElementLocation(element);
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        mCurrentDepth--;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mMaxDepthFound > mMaxDepth && mRootLocation != null) {
            String message =
                    "Layout hierarchy is too deep: "
                            + mMaxDepthFound
                            + " levels (maximum recommended is "
                            + mMaxDepth
                            + "). Layouts with too much nesting are bad for performance. Consider using a flatter layout such as RelativeLayout or GridLayout.";
            context.report(ISSUE, mRootLocation, message);
        }
    }
}