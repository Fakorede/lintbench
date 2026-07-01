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
                            + " in this layout.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mCount;

    @Override
    public void beforeCheckFile(Context context) {
        mCount = 0;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        mCount++;
    }

    @Override
    public void visitElementAfter(XmlContext context, org.w3c.dom.Element element) {
        if (element.getParentNode().getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) {
            int max = getMaxViewCount();
            if (mCount > max) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "Layout has too many views (%1$d, maximum is %2$d)",
                                mCount, max));
            }
        }
    }

    private static int getMaxViewCount() {
        String max = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (max != null) {
            try {
                return Integer.parseInt(max);
            } catch (NumberFormatException e) {
                // Fall through to default.
            }
        }
        return 80;
    }
}