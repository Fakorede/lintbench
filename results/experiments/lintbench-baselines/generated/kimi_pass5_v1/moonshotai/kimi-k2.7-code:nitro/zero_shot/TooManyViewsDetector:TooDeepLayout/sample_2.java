package com.android.tools.lint.checks;

import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.TAG_MERGE;
import static com.android.SdkConstants.TAG_REQUEST_FOCUS;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends LayoutDetector {

    private static final int DEFAULT_MAX_DEPTH = 10;
    private static final String ENV_MAX_DEPTH = "ANDROID_LINT_MAX_DEPTH";

    public static final Issue ISSUE_TOO_DEEP = Issue.create(
            "TooDeepLayout",
            "Layout hierarchy is too deep",
            "Layouts with too much nesting is bad for performance. Consider using a flatter layout such as `RelativeLayout` or `GridLayout`. The default maximum depth is 10 but can be configured with the environment variable `ANDROID_LINT_MAX_DEPTH`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mMaxDepth = DEFAULT_MAX_DEPTH;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        String env = System.getenv(ENV_MAX_DEPTH);
        if (env != null && !env.isEmpty()) {
            try {
                mMaxDepth = Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
                mMaxDepth = DEFAULT_MAX_DEPTH;
            }
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        int maxDepth = getMaxDepth(root, 0);
        if (maxDepth > mMaxDepth) {
            Location location = context.getLocation(root);
            context.report(ISSUE_TOO_DEEP, root, location,
                    String.format("Layout has too deep a hierarchy: depth %1$d, maximum %2$d", maxDepth, mMaxDepth));
        }
    }

    private static int getMaxDepth(@NonNull Element element, int depth) {
        String tag = element.getTagName();

        if (TAG_INCLUDE.equals(tag) || TAG_REQUEST_FOCUS.equals(tag)) {
            return depth;
        }

        int currentDepth = depth;
        if (!TAG_MERGE.equals(tag)) {
            currentDepth++;
        }

        int max = currentDepth;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                max = Math.max(max, getMaxDepth((Element) child, currentDepth));
            }
        }
        return max;
    }
}