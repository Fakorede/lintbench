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
                    "The maximum view count defaults to 80 but can be configured with the " +
                    "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    private int mViewCount;
    private int mMaxViewCount;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mViewCount = 0;
        String max = System.getenv(ENV_MAX_VIEW_COUNT);
        if (max != null) {
            try {
                mMaxViewCount = Integer.parseInt(max);
            } catch (NumberFormatException e) {
                mMaxViewCount = DEFAULT_MAX_VIEW_COUNT;
            }
        } else {
            mMaxViewCount = DEFAULT_MAX_VIEW_COUNT;
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
        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.DOCUMENT_NODE) {
            if (mViewCount > mMaxViewCount) {
                String message = String.format(
                        "This layout has too many views (%1$d views, should be <= %2$d); " +
                        "consider using compound drawables or other tricks for reducing the number of views in this layout.",
                        mViewCount, mMaxViewCount);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }
}