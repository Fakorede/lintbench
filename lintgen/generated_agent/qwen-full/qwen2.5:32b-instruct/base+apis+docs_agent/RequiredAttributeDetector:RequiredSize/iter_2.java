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
import java.util.List;

public class RequiredAttributeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingRequiredAttributes",
            "All views must specify an explicit `layout_width` and `layout_height` attribute.",
            "Views in Android layouts must have both `layout_width` and `layout_height` attributes specified. GridLayout is a special case where these attributes are not required.",
            Category.CORRECTNESS,
            6, // Severity
            Severity.ERROR,
            new Implementation(RequiredAttributeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("GridLayout".equals(element.getTagName())) {
            // GridLayout does not require layout_width and layout_height attributes.
            return;
        }
        
        boolean hasLayoutWidth = false;
        boolean hasLayoutHeight = false;

        for (Attr attribute : getAttributes(context, element)) {
            String attributeName = attribute.getName();
            if ("layout_width".equals(attributeName) || "layout_height".equals(attributeName)) {
                if ("layout_width".equals(attributeName)) {
                    hasLayoutWidth = true;
                } else if ("layout_height".equals(attributeName)) {
                    hasLayoutHeight = true;
                }
            }
        }

        if (!hasLayoutWidth && !element.getTagName().startsWith("GridLayout")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `layout_width` attribute");
        }

        if (!hasLayoutHeight && !element.getTagName().startsWith("GridLayout")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `layout_height` attribute");
        }
    }

    private List<Attr> getAttributes(XmlContext context, Element element) {
        return context.getXmlDocument().getElementsByTagName("*").item(0).getAttributes()
                .getNamedItem(element.getAttributeNode("name"));
    }
}