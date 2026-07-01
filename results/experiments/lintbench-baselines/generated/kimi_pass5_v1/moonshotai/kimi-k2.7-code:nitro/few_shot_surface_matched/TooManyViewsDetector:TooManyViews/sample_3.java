package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider using"
                            + " compound drawables or other tricks for reducing the number of views"
                            + " in this layout.\n"
                            + "\n"
                            + "The maximum view count defaults to 80 but can be configured with the"
                            + " environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX = 80;

    private int mCount;
    private int mMax;

    @Override
    public void beforeCheckFile(Context context) {
        super.beforeCheckFile(context);
        mCount = 0;
        int max = getMaxViews(context);
        if (max < 0) {
            max = DEFAULT_MAX;
        }
        mMax = max;
    }

    private static int getMaxViews(Context context) {
        String value = context.getClient().getEnvironmentVariable("ANDROID_LINT_MAX_VIEW_COUNT");
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ignore invalid values
            }
        }
        return DEFAULT_MAX;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (LayoutDetector.isLayout(element) || LayoutDetector.isView(element)) {
            mCount++;
            if (mCount > mMax) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "This layout has too many views: %1$d (maximum is %2$d);"
                                        + " consider using a compound drawable or other tricks"
                                        + " for reducing the number of views",
                                mCount,
                                mMax));
            }
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, org.w3c.dom.Element element) {
    }
}