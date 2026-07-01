package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;

public class TooManyViewsDetector extends LayoutDetector {

    private static final String KEY_VIEW_COUNT = "viewCount";
    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. " +
            "Consider using compound drawables or other tricks for reducing the number of views in this layout.\n\n" +
            "The maximum view count defaults to 80 but can be configured with the environment variable `ANDROID_LINT_MAX_VIEW_COUNT`.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(Context context) {
        context.putClientData(KEY_VIEW_COUNT, 0);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Integer count = (Integer) context.getClientData(KEY_VIEW_COUNT);
        if (count != null) {
            context.putClientData(KEY_VIEW_COUNT, count + 1);
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        Integer count = (Integer) context.getClientData(KEY_VIEW_COUNT);
        if (count != null) {
            int max = getMaxViewCount();
            if (count > max) {
                XmlContext xmlContext = (XmlContext) context;
                Element root = xmlContext.document.getDocumentElement();
                if (root != null) {
                    String message = String.format("This layout has too many views (%1$d views, max is %2$d)", count, max);
                    context.report(ISSUE, root, context.getLocation(root), message);
                }
            }
        }
    }

    private static int getMaxViewCount() {
        String env = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException e) {
                // Ignore invalid values, fall back to default
            }
        }
        return DEFAULT_MAX_VIEW_COUNT;
    }
}