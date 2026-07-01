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
import java.util.Collection;
import org.w3c.dom.Element;

public class TooManyViewsDetector extends LayoutDetector {

    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";
    private static final int DEFAULT_MAX_DEPTH = 10;

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`).",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private int maxDepth;
    private int currentDepth;
    private boolean reported;

    @Override
    public void beforeCheckProject(Context context) {
        maxDepth = DEFAULT_MAX_DEPTH;
        String value = System.getenv(ENV_MAX_DEPTH);
        if (value != null) {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed > 0) {
                    maxDepth = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Override
    public void beforeCheckFile(Context context) {
        currentDepth = 0;
        reported = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        currentDepth++;
        if (!reported && currentDepth > maxDepth) {
            reported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Layout hierarchy is too deep: %1$d (maximum allowed is %2$d)",
                            currentDepth, maxDepth));
        }
    }

    @Override
    public void visitElementEnd(XmlContext context, Element element) {
        currentDepth--;
    }
}