package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element parent = element.getParentNode() instanceof Element ? (Element) element.getParentNode() : null;
        if (parent == null || !parent.getTagName().equals(SdkConstants.TAG_RESOURCES)) {
            return;
        }

        String tagName = element.getTagName();
        String definedType;

        if (tagName.equals(SdkConstants.TAG_ITEM)) {
            definedType = element.getAttribute(SdkConstants.ATTR_TYPE);
            if (definedType == null || definedType.isEmpty()) {
                return;
            }
        } else {
            definedType = tagName;
        }

        // Normalize defined type to match reference prefixes used in @type/name
        if ("string-array".equals(definedType) || "integer-array".equals(definedType)) {
            definedType = "array";
        } else if ("plurals".equals(definedType)) {
            definedType = "plurals";
        }

        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        text = text.trim();

        if (text.startsWith("@")) {
            int slashIndex = text.indexOf('/');
            if (slashIndex > 0) {
                int colonIndex = text.lastIndexOf(':', slashIndex);
                int typeStart = (colonIndex != -1) ? colonIndex + 1 : (text.startsWith("@+") ? 2 : 1);
                String refType = text.substring(typeStart, slashIndex);

                if (!definedType.equals(refType)) {
                    String message = String.format(
                            "Resource alias of type `%1$s` references a resource of type `%2$s`; they must match",
                            definedType, refType);
                    context.report(ISSUE, element, context.getLocation(element), message);
                }
            }
        }
    }
}