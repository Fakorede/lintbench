package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector {

    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured with the "
                            + "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mViewCount;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mViewCount++;
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getOwnerDocument().getDocumentElement() == element) {
            int max = getMaxViewCount();
            if (mViewCount > max) {
                context.report(ISSUE, element, context.getLocation(element),
                        "This layout has too many views (" + mViewCount + " views, max is " + max + "); "
                                + "consider using compound drawables or other tricks for reducing the number of views in this layout.");
            }
        }
    }

    private static int getMaxViewCount() {
        String env = System.getenv(ENV_MAX_VIEW_COUNT);
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Ignore invalid environment variable values
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}