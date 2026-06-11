package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class ObsoleteLayoutParamsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParams",
            "The given layout_param is not defined for the given layout, meaning it has no effect.",
            "This usually happens when you change the parent layout or move view code around without updating the layout params. This will cause useless attribute processing at runtime and is misleading for others reading the layout so the parameter should be removed.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check for layout parameters that are not defined in the current layout type.
        for (Attr attr : element.getAttributes()) {
            String attributeName = attr.getName();
            if (!isValidLayoutParams(context, element, attributeName)) {
                context.report(ISSUE, element, context.getLocation(attr), "The attribute '" + attributeName + "' is not defined for the given layout and has no effect.");
            }
        }
    }

    private boolean isValidLayoutParams(XmlContext context, Element element, String attributeName) {
        // Implement logic to check if the attribute name is valid for the current layout type.
        // This can be done by checking against a predefined list of attributes that are valid
        // for each layout type (e.g., LinearLayout, RelativeLayout).
        // For simplicity, this method returns false if the attribute name contains "layout_".
        return !attributeName.startsWith("layout_");
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {}

    @Override
    public void visitDocument(XmlContext context, Element document) {}

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}