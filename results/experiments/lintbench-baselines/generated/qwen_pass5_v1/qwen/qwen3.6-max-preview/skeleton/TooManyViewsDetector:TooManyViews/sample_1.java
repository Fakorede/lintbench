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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
                    "The maximum view count defaults to 80 but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;
    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";

    private int mViewCount;

    private static int getMaxViewCount() {
        String value = System.getenv(ENV_MAX_VIEW_COUNT);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }

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
        String tag = element.getTagName();
        if (!tag.equals("include") && !tag.equals("merge") &&
            !tag.equals("requestFocus") && !tag.equals("tag")) {
            mViewCount++;
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getOwnerDocument() != null && element.getOwnerDocument().getDocumentElement() == element) {
            int max = getMaxViewCount();
            if (mViewCount > max) {
                context.report(ISSUE, element, context.getLocation(element),
                        String.format("This layout has too many views: %1$d views, it should have <= %2$d!",
                                mViewCount, max));
            }
        }
    }
}