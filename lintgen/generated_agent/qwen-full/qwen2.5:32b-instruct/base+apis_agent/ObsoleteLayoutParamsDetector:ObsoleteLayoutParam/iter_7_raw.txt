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
        for (Attr attribute : getAttributes(element)) {
            String attributeName = attribute.getName();
            if (!isValidLayoutParamsForView(context, element, attributeName)) {
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

    private Set<Attr> getAttributes(Element element) {
        Set<Attr> attributes = new HashSet<>();
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            attributes.add((Attr) element.getAttributes().item(i));
        }
        return attributes;
    }

    private boolean isValidLayoutParamsForView(XmlContext context, Element element, String attributeName) {
        Set<String> validParams = getValidLayoutParams(element.getTagName());
        return validParams.contains(attributeName);
    }

    private Set<String> getValidLayoutParams(String viewType) {
        switch (viewType.toLowerCase()) {
            case "linearlayout":
                return new HashSet<>(Arrays.asList(SdkConstants.ATTR_ORIENTATION));
            case "relativelayout":
                return Collections.emptySet();
            default:
                return Collections.emptySet();
        }
    }
}