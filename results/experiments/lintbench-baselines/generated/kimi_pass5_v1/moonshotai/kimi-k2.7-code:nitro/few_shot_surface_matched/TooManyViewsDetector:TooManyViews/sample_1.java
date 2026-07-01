package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider"
                            + " using compound drawables or other tricks for reducing the number"
                            + " of views in this layout.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mCount;
    private int mMax;
    private Element mRoot;

    @Override
    public void beforeCheckFile(XmlContext context) {
        mCount = 0;
        mRoot = null;

        if (mMax == 0) {
            String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
            if (env != null) {
                try {
                    mMax = Integer.parseInt(env);
                } catch (NumberFormatException e) {
                    mMax = DEFAULT_MAX_VIEW_COUNT;
                }
            } else {
                mMax = DEFAULT_MAX_VIEW_COUNT;
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mRoot == null) {
            mRoot = element;
        }
        mCount++;
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (element == mRoot && mCount > mMax) {
            String message =
                    String.format(
                            "Layout has too many views (%1$d, current maximum is %2$d);"
                                    + " using too many views in a single layout is bad for"
                                    + " performance",
                            mCount,
                            mMax);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}