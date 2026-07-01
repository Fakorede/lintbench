package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_VIEWS = 80;
    private static final String MAX_VIEW_COUNT_ENV = "ANDROID_LINT_MAX_VIEW_COUNT";

    private int mChildCount;

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing "
                            + "the number of views in this layout. The maximum view count "
                            + "defaults to 80 but can be configured with the environment variable "
                            + "`ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mChildCount = 0;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (!tag.isEmpty() && Character.isUpperCase(tag.charAt(0))) {
            mChildCount++;
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        int maxViews = getMaxViews();
        if (mChildCount > maxViews) {
            String message = String.format(
                    "Layout has too many views: %1$d (maximum allowed: %2$d)",
                    mChildCount,
                    maxViews);
            Location location = Location.create(context.file);
            context.report(ISSUE, location, message);
        }
    }

    private int getMaxViews() {
        String value = System.getenv(MAX_VIEW_COUNT_ENV);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // fall back to default
            }
        }
        return DEFAULT_MAX_VIEWS;
    }
}