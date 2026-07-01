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

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance. Consider using a flatter layout "
                            + "(such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 "
                            + "but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int depth;
    private int maxDepth = 10;
    private boolean reported;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        depth = 0;
        reported = false;

        String maxDepthVar = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (maxDepthVar != null) {
            try {
                maxDepth = Integer.parseInt(maxDepthVar);
            } catch (NumberFormatException e) {
                maxDepth = 10;
            }
        } else {
            maxDepth = 10;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        depth++;
        if (depth > maxDepth && !reported) {
            reported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Layout hierarchy is too deep (%d > %d)", depth, maxDepth));
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        depth--;
    }
}