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
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. " +
                    "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
                    "The maximum view count defaults to 80 but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mViewCount;
    private int mMaxCount;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
        mMaxCount = 80;
        String max = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (max != null) {
            try {
                mMaxCount = Integer.parseInt(max);
            } catch (NumberFormatException ignored) {
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
        if (element.getParentNode() != null && element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
            if (mViewCount > mMaxCount) {
                String message = String.format(
                        "This layout has too many views (%1$d views, should be <= %2$d)",
                        mViewCount, mMaxCount);
                context.report(ISSUE, context.getLocation(element), message);
            }
        }
    }
}