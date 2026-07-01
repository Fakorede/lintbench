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
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TooManyViewsDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "TooDeepLayout",
                    "Layout hierarchy is too deep",
                    "Layouts with too much nesting is bad for performance. Consider using "
                            + "a flatter layout (such as RelativeLayout or GridLayout). "
                            + "The default maximum depth is 10 but can be configured with the "
                            + "environment variable ANDROID_LINT_MAX_DEPTH.",
                    Category.PERFORMANCE,
                    1,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mMaxDepth = 10;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        String val = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (val != null) {
            try {
                mMaxDepth = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                mMaxDepth = 10;
            }
        } else {
            mMaxDepth = 10;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        int depth = 0;
        Node current = element;
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                depth++;
            }
            current = current.getParentNode();
        }

        if (depth == mMaxDepth + 1) {
            String message = String.format("Layout hierarchy is too deep (%d) > %d", depth, mMaxDepth);
            context.report(ISSUE, element, context.getNameLocation(element), message);
        }
    }

    @Override
    public void visitElementAfter(@NonNull XmlContext context, @NonNull Element element) {
        // No action required
    }
}