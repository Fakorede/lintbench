package com.android.tools.lint.checks;

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
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider "
                            + "using compound drawables or other tricks for reducing the number "
                            + "of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured with the "
                            + "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            TooManyViewsDetector.class, Scope.LAYOUT_RESOURCE_SCOPE));

    private int mViewCount;
    private Element mRoot;

    @Override
    public void beforeCheckFile(XmlContext context) {
        mViewCount = 0;
        mRoot = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mRoot == null) {
            mRoot = element;
        }
        mViewCount++;
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (element == mRoot) {
            int max = getMaxViewCount();
            if (mViewCount > max) {
                String message = String.format(
                        "This layout has too many views (%1$d) (limit %2$d)",
                        mViewCount, max);
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }
    }

    private static int getMaxViewCount() {
        String value = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 80;
    }
}