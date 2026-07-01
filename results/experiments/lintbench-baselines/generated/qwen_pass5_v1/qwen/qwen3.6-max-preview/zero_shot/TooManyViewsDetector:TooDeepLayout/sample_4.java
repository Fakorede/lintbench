package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.List;

public class TooManyViewsDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";

    private int cachedMaxDepth = -1;

    private int getMaxDepth() {
        if (cachedMaxDepth != -1) {
            return cachedMaxDepth;
        }
        String env = System.getenv(ENV_MAX_DEPTH);
        if (env != null) {
            try {
                cachedMaxDepth = Integer.parseInt(env);
            } catch (NumberFormatException e) {
                cachedMaxDepth = DEFAULT_MAX_DEPTH;
            }
        } else {
            cachedMaxDepth = DEFAULT_MAX_DEPTH;
        }
        return cachedMaxDepth;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public List<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        int depth = 0;
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent instanceof Element) {
                depth++;
            }
            parent = parent.getParentNode();
        }

        int limit = getMaxDepth();
        if (depth == limit + 1) {
            String message = String.format("Layout hierarchy is too deep: depth is %d (max is %d)", depth, limit);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}