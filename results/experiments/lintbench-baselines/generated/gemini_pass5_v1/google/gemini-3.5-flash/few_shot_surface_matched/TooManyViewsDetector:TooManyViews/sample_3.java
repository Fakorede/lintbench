package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "TooManyViews",
                    "Layout has too many views",
                    "Using too many views in a single layout is bad for performance. Consider "
                            + "using compound drawables or other tricks for reducing the number "
                            + "of views in this layout.\n\n"
                            + "The maximum view count defaults to 80 but can be configured with the "
                            + "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(TooManyViewsDetector.class, Scope.LAYOUT_SCOPE));

    private int viewCount;
    private int maxViewCount = 80;

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        viewCount = 0;
        maxViewCount = 80;
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                maxViewCount = Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Ignore and use default
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        viewCount++;
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Document) {
            if (viewCount > maxViewCount) {
                String message = String.format(
                        "This layout has too many views (%d); the limit is %d",
                        viewCount, maxViewCount);
                context.report(ISSUE, element, context.getNameLocation(element), message);
            }
        }
    }
}