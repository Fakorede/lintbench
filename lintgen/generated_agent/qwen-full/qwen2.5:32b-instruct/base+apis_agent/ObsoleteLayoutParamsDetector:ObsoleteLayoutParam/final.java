package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlScanner;

public class ObsoleteLayoutParamsDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ObsoleteLayoutParams",
            "The given layout_param is not defined for the given layout, meaning it has no effect.",
            "This usually happens when you change the parent layout or move view code around without updating the layout params. This will cause useless attribute processing at runtime and is misleading for others reading the layout so the parameter should be removed.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    ObsoleteLayoutParamsDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public Set<String> getApplicableElements() {
        return Collections.emptySet();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attribute = (Attr) element.getAttributes().item(i);
            String attributeName = attribute.getName();
            if (!isValidLayoutParamsForView(element.getTagName(), attributeName)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(attribute),
                        "The layout parameter '" + attributeName + "' is not defined for the given view and has no effect."
                );
            }
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {}

    @Override
    public Set<String> getApplicableAttributes() {
        return Collections.emptySet();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {}

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {}

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }

    private boolean isValidLayoutParamsForView(String viewType, String attributeName) {
        Set<String> validParams = getValidLayoutParams(viewType);
        return validParams.contains(attributeName);
    }

    private Set<String> getValidLayoutParams(String viewType) {
        switch (viewType.toLowerCase()) {
            case "linearlayout":
                return new HashSet<>(Arrays.asList(SdkConstants.ATTR_ORIENTATION, SdkConstants.LAYOUT_WEIGHT));
            case "relativelayout":
                return new HashSet<>(Arrays.asList(
                        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT,
                        SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP,
                        SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT
                ));
            default:
                return Collections.emptySet();
        }
    }
}