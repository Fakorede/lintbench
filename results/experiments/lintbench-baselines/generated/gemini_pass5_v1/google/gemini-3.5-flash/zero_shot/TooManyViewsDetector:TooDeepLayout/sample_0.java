package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using " +
            "a flatter layout (such as `RelativeLayout` or `GridLayout`). " +
            "The default maximum depth is 10 but can be configured with the environment " +
            "variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            4,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final int DEFAULT_MAX_DEPTH = 10;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            int maxDepth = getMaxDepth();
            checkDepth(context, root, 1, maxDepth);
        }
    }

    private int getMaxDepth() {
        String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // fallback to default
            }
        }
        return DEFAULT_MAX_DEPTH;
    }

    private void checkDepth(XmlContext context, Element element, int depth, int maxDepth) {
        if (depth > maxDepth) {
            String message = String.format("Layout hierarchy is too deep (%d), maximum is %d", depth, maxDepth);
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    message
            );
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkDepth(context, (Element) child, depth + 1, maxDepth);
            }
        }
    }
}