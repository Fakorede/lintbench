package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.util.Collection;

public class TooManyViewsDetector extends LayoutDetector {
    private static final String MAX_VIEW_COUNT_PROPERTY = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private static final int MAX_VIEW_COUNT = getMaxViewCount();

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. "
                    + "Consider using compound drawables or other tricks for "
                    + "reducing the number of views in this layout.\n\n"
                    + "The maximum view count defaults to 80 but can be configured "
                    + "with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mViews;

    private static int getMaxViewCount() {
        String max = System.getenv(MAX_VIEW_COUNT_PROPERTY);
        if (max != null) {
            try {
                return Integer.parseInt(max);
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void beforeCheckFile(@NotNull Context context) {
        mViews = 0;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        mViews++;
    }

    @Override
    public void afterCheckFile(@NotNull Context context) {
        if (mViews > MAX_VIEW_COUNT) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.getDocument().getDocumentElement();
            String message = String.format(
                    "This layout has too many views: %1$d (maximum: %2$d)",
                    mViews, MAX_VIEW_COUNT);
            xmlContext.report(ISSUE, root, xmlContext.getLocation(root), message);
        }
        mViews = 0;
    }
}