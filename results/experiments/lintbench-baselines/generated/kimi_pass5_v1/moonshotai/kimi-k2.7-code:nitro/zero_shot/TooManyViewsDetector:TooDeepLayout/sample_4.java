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
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TooManyViewsDetector extends LayoutDetector {

    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";
    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final int MAX_DEPTH;

    static {
        int max = DEFAULT_MAX_DEPTH;
        String value = System.getenv(ENV_MAX_DEPTH);
        if (value != null) {
            try {
                max = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        MAX_DEPTH = max;
    }

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting are bad for performance. Consider using a flatter "
                    + "layout (such as <code>RelativeLayout</code> or <code>GridLayout</code>). "
                    + "The default maximum depth is 10 but can be configured with the environment "
                    + "variable <code>ANDROID_LINT_MAX_DEPTH</code>.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private boolean mReported;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mReported = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mReported) {
            return;
        }

        int depth = getDepth(element);
        if (depth > MAX_DEPTH) {
            mReported = true;
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Layout hierarchy is too deep: depth %1$d (maximum %2$d)",
                            depth,
                            MAX_DEPTH));
        }
    }

    private static int getDepth(@NonNull Element element) {
        int depth = 1;
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            depth++;
            parent = parent.getParentNode();
        }
        return depth;
    }
}