package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

    private static final int DEFAULT_MAX_VIEWS = 80;
    private static final String ENVIRONMENTAL_VARIABLE = "ANDROID_LINT_MAX_VIEW_COUNT";

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for "
                            + "reducing the number of views in this layout.\n\n"
                            + "The maximum view count defaults to " + DEFAULT_MAX_VIEWS
                            + " but can be configured with the environment variable "
                            + "`" + ENVIRONMENTAL_VARIABLE + "`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mChildCount;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mChildCount = 0;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mChildCount++;
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        if (element == context.getDocument().getDocumentElement()) {
            int max = getMaxViews();
            if (mChildCount > max) {
                String message = String.format(
                        "Layout has too many views: %1$d (maximum: %2$d)",
                        mChildCount,
                        max);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    private static int getMaxViews() {
        String value = System.getenv(ENVIRONMENTAL_VARIABLE);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Fall back to default
            }
        }
        return DEFAULT_MAX_VIEWS;
    }
}