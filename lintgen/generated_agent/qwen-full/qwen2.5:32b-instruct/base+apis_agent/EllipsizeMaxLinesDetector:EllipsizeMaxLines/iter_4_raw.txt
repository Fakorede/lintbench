package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;

public class EllipsizeMaxLinesDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeWithMaxLines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices.",
            "Earlier versions of lint recommended replacing `singleLine=true` with  `maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            5, // Priority
            Severity.ERROR,
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("TextView");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr ellipsizeAttr = getAttribute(element, "ellipsize");
        Attr maxLinesAttr = getAttribute(element, "maxLines");

        if (ellipsizeAttr != null && maxLinesAttr != null) {
            String maxLinesValue = maxLinesAttr.getValue();
            if ("1".equals(maxLinesValue)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices.");
            }
        }
    }

    private Attr getAttribute(Element element, String attributeName) {
        return (Attr) element.getAttributeNode(attributeName);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType)
                || ResourceFolderType.MENU.equals(folderType);
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        // No-op
    }
}