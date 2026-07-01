package com.android.tools.lint.checks;

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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TooManyViewsDetector extends ResourceXmlDetector {
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

    private static final String ENV_VAR = "ANDROID_LINT_MAX_VIEW_COUNT";
    private static final int DEFAULT_MAX = 80;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        Document document = xmlContext.document;
        if (document == null || document.getDocumentElement() == null) {
            return;
        }

        int count = countViews(document.getDocumentElement());
        int max = getMaxViewCount();

        if (count > max) {
            String message = String.format(
                "This layout has too many views (%1$d views, max allowed is %2$d); consider using compound drawables or other tricks for reducing the number of views in this layout",
                count, max);
            context.report(ISSUE, xmlContext.getLocation(document.getDocumentElement()), message);
        }
    }

    private static int countViews(Node node) {
        int count = 0;
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            count++;
            NodeList children = node.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                count += countViews(children.item(i));
            }
        }
        return count;
    }

    private static int getMaxViewCount() {
        String env = System.getenv(ENV_VAR);
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_MAX;
    }
}