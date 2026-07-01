package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final String MAX_VIEW_COUNT_ENV = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEWS = 80;
    private static int sMaxViews = -1;

    private int mViews;

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing "
                            + "the number of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured with "
                            + "the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(Context context) {
        mViews = 0;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null; // visit every element
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        mViews++;
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (element == context.document.getDocumentElement()) {
            int max = getMaxViews();
            if (mViews > max) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "Layout has too many views: %1$d (maximum: %2$d)",
                                mViews,
                                max));
            }
        }
    }

    private static int getMaxViews() {
        if (sMaxViews < 0) {
            String env = System.getenv(MAX_VIEW_COUNT_ENV);
            if (env != null) {
                try {
                    sMaxViews = Integer.parseInt(env);
                } catch (NumberFormatException e) {
                    sMaxViews = DEFAULT_MAX_VIEWS;
                }
            } else {
                sMaxViews = DEFAULT_MAX_VIEWS;
            }
        }
        return sMaxViews;
    }
}