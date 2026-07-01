package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a " +
            "flatter layout (such as `RelativeLayout` or `GridLayout`). The default " +
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

        int maxDepth = getMaxDepthValue();
        int depth = getMaxDepth(root);
        if (depth > maxDepth) {
            context.report(
                    ISSUE,
                    root,
                    context.getNameLocation(root),
                    String.format("Layout has too much nesting (%d levels; limit is %d)", depth, maxDepth)
            );
        }
    }

    private int getMaxDepth(Element element) {
        int max = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                max = Math.max(max, getMaxDepth((Element) child));
            }
        }
        return max + 1;
    }

    private static int getMaxDepthValue() {
        String val = System.getenv("ANDROID_LINT_MAX_DEPTH");
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return 10;
    }
}