package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends LayoutDetector {
    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            3,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        int count = countViews(document.getDocumentElement());
        int max = getMaxViewCount();
        if (count > max) {
            String message = String.format("This layout has too many views (%d > %d)", count, max);
            context.report(ISSUE, context.getLocation(document.getDocumentElement()), message);
        }
    }

    private int countViews(Element element) {
        int count = 0;
        String tag = element.getTagName();
        if (!isNonViewTag(tag)) {
            count++;
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count += countViews((Element) child);
            }
        }
        return count;
    }

    private boolean isNonViewTag(String tag) {
        return "merge".equals(tag) || "include".equals(tag) ||
               "requestFocus".equals(tag) || "tag".equals(tag);
    }

    private int getMaxViewCount() {
        String env = System.getenv(ENV_MAX_VIEW_COUNT);
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}