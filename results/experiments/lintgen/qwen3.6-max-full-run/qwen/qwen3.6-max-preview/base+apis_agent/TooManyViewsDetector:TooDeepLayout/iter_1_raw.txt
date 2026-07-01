package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;

public class TooManyViewsDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance.  Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment  variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            1,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int depth = 0;
        Node node = element;
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                depth++;
            }
            node = node.getParentNode();
        }

        int maxDepth = getMaxDepth();
        if (depth > maxDepth) {
            Node parent = element.getParentNode();
            boolean parentExceeded = false;
            if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                if (depth - 1 > maxDepth) {
                    parentExceeded = true;
                }
            }
            if (!parentExceeded) {
                context.report(ISSUE, context.getLocation(element),
                        "Layout hierarchy is too deep: depth is " + depth + " (max is " + maxDepth + ")");
            }
        }
    }

    private static int getMaxDepth() {
        String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        return 10;
    }
}