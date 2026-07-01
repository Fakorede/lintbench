package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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

    public static final Issue ISSUE = Issue.create(
        "TooManyViews",
        "Layout has too many views",
        "Using too many views in a single layout is bad for performance. Consider " +
        "using compound drawables or other tricks for reducing the number of views in this layout.",
        Category.PERFORMANCE,
        5,
        Severity.WARNING,
        new Implementation(
            TooManyViewsDetector.class,
            Scope.RESOURCE_FILE_SCOPE
        )
    );

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    private int getMaxViewCount() {
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Ignore and fall back to default
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        int viewCount = countViews(root);
        int max = getMaxViewCount();
        if (viewCount > max) {
            String message = String.format(
                "%s has more than %d views (%d)",
                context.file.getName(), max, viewCount
            );
            context.report(ISSUE, root, context.getNameLocation(root), message);
        }
    }

    private int countViews(Element element) {
        String tagName = element.getTagName();
        int count = 0;

        // Do not count non-view structural tags
        if (!tagName.equals("layout") &&
            !tagName.equals("data") &&
            !tagName.equals("variable") &&
            !tagName.equals("import") &&
            !tagName.equals("merge") &&
            !tagName.equals("requestFocus") &&
            !tagName.equals("tag")) {
            count = 1;
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
}