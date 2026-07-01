package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class TooManyViewsDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the " +
            "environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final int DEFAULT_MAX_VIEWS = 80;

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Counting is deferred to afterCheckFile to avoid per-element overhead
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        if (xmlContext.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }

        Element root = xmlContext.getDocument().getDocumentElement();
        if (root == null) {
            return;
        }

        int count = countElements(root);
        int max = getMaxViewCount();
        if (count > max) {
            String message = String.format("This layout has too many views (%1$d > %2$d)", count, max);
            xmlContext.report(ISSUE, xmlContext.getLocation(root), message);
        }
    }

    private static int countElements(@NonNull Element element) {
        int count = 1;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count += countElements((Element) child);
            }
        }
        return count;
    }

    private static int getMaxViewCount() {
        String value = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore invalid configuration
            }
        }
        return DEFAULT_MAX_VIEWS;
    }
}