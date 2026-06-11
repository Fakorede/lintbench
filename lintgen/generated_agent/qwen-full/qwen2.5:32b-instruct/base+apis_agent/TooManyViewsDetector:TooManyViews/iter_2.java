package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class TooManyViewsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TooManyViews",
            "Layout has too many views",
            "Using too many views in a single layout is bad for performance. Consider using compound drawables or other tricks for reducing the number of views in this layout.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(TooManyViewsDetector.class, Scope.MANIFEST_SCOPE));

    private static final int DEFAULT_MAX_VIEW_COUNT = 80;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    private int viewCount = 0;
    private int maxViewCount;

    @Override
    public void visitDocument(XmlContext context, Document document) {
        String envMaxViewCount = System.getenv("ANDROID_LINT_MAX_VIEW_COUNT");
        if (envMaxViewCount != null) {
            try {
                maxViewCount = Integer.parseInt(envMaxViewCount);
            } catch (NumberFormatException e) {
                // Use default value in case of invalid input
                maxViewCount = DEFAULT_MAX_VIEW_COUNT;
            }
        } else {
            maxViewCount = DEFAULT_MAX_VIEW_COUNT;
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.getTagName().startsWith("android.view.")) {
            viewCount++;
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (viewCount > maxViewCount && ResourceFolderType.LAYOUT.equals(context.getFile().getFolderType())) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This layout has too many views (" + viewCount + "). Consider reducing the number of views.");
        }
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {}
}