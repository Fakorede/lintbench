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
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Too many views in layout",
                    "Using too many views in a single layout is bad for performance. "
                            + "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured with the "
                            + "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mViewCount;
    private int mMaxViews;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
        mMaxViews = 80;
        String maxStr = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (maxStr != null) {
            try {
                mMaxViews = Integer.parseInt(maxStr);
            } catch (NumberFormatException ignored) {
                // Fall back to default
            }
        }
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
            if (mViewCount > mMaxViews) {
                String message = String.format(
                        "This layout has too many views: %d views, the maximum allowed is %d. "
                                + "Consider using compound drawables or other tricks for reducing the number of views in this layout.",
                        mViewCount, mMaxViews);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }
}