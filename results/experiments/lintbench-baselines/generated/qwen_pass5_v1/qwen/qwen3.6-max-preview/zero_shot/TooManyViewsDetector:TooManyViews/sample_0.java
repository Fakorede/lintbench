package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            7,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    @Override
    public void afterCheckFile(@NotNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            Document document = xmlContext.getDocument();
            if (document != null) {
                Element root = document.getDocumentElement();
                if (root != null) {
                    int count = countViews(root);
                    int max = getMaxViewCount();
                    if (count > max) {
                        String message = String.format(
                                "This layout has too many views (%1$d views, max is %2$d); consider using compound drawables or other tricks for reducing the number of views in this layout.",
                                count, max);
                        xmlContext.report(ISSUE, root, xmlContext.getLocation(root), message);
                    }
                }
            }
        }
    }

    private static int countViews(@Nullable Element element) {
        if (element == null) {
            return 0;
        }
        int count = 1;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count += countViews((Element) child);
            }
        }
        return count;
    }

    private static int getMaxViewCount() {
        String max = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (max != null) {
            try {
                return Integer.parseInt(max);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}