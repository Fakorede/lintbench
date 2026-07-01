package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends ResourceXmlDetector {

    private static final String ENV_MAX_VIEW_COUNT = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. "
                    + "Consider using compound drawables or other tricks for reducing the number of views in this layout.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private int mMaxViewCount = DEFAULT_MAX_VIEW_COUNT;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        String max = System.getenv(ENV_MAX_VIEW_COUNT);
        if (max != null) {
            try {
                int value = Integer.parseInt(max);
                if (value > 0) {
                    mMaxViewCount = value;
                }
            } catch (NumberFormatException e) {
                // Ignore invalid configuration values and use the default.
            }
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        int count = countViews(root);
        if (count > mMaxViewCount) {
            context.report(
                    ISSUE,
                    context.getElementLocation(root),
                    String.format("Layout has too many views: %1$d (maximum %2$d)", count, mMaxViewCount));
        }
    }

    private static int countViews(@NonNull Element element) {
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
}