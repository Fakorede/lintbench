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

    private static final String MAX_DEPTH_ENV = "ANDROID_LINT_MAX_DEPTH";
    private static final int DEFAULT_MAX_DEPTH = 10;

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting are bad for performance. "
                            + "Consider using a flatter layout such as RelativeLayout, "
                            + "GridLayout or ConstraintLayout, or using <merge> and <include>.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mDepth;
    private boolean mReported;

    @Override
    public void beforeCheckFile(Context context) {
        mDepth = 0;
        mReported = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null; // visit every element
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mDepth++;
        int maxDepth = getMaxDepth();
        if (mDepth > maxDepth && !mReported) {
            mReported = true;
            String message =
                    String.format(
                            "Layout hierarchy is too deep: depth is %1$d, maximum is %2$d",
                            mDepth, maxDepth);
            Location location = context.getLocation(element);
            context.report(ISSUE, element, location, message);
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        mDepth--;
    }

    private static int getMaxDepth() {
        String value = System.getenv(MAX_DEPTH_ENV);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return DEFAULT_MAX_DEPTH;
    }
}