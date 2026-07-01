package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.annotations.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using " +
            "a flatter layout (such as `RelativeLayout` or `GridLayout`). The default " +
            "maximum depth is 10 but can be configured with the environment " +
            "variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    TooManyViewsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final int DEFAULT_MAX_DEPTH = 10;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        int maxDepth = DEFAULT_MAX_DEPTH;
        String env = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (env != null) {
            try {
                maxDepth = Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // ignore, use default
            }
        }

        checkElement(context, root, 1, maxDepth);
    }

    private boolean checkElement(@NonNull XmlContext context, @NonNull Element element, int depth, int maxDepth) {
        if (depth > maxDepth) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Layout hierarchy is too deep (%d) limit is %d", depth, maxDepth)
            );
            return true;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (checkElement(context, (Element) child, depth + 1, maxDepth)) {
                    return true;
                }
            }
        }
        return false;
    }
}