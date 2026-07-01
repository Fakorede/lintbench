package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout (such as `RelativeLayout` or `GridLayout`). The default maximum depth is 10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";
    private static final int DEFAULT_MAX_DEPTH = 10;

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        int maxDepth = getMaxDepth();
        int depth = getDepth(element);
        if (depth == maxDepth + 1) {
            String message = String.format(
                    "Layout hierarchy is too deep: %1$d (max is %2$d). Consider using a flatter layout.",
                    depth, maxDepth);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static int getDepth(@NotNull Element element) {
        int depth = 0;
        Node parent = element.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                depth++;
            }
            parent = parent.getParentNode();
        }
        return depth;
    }

    private static int getMaxDepth() {
        String env = System.getenv(ENV_MAX_DEPTH);
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_DEPTH;
    }
}