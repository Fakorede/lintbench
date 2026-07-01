package com.android.tools.lint.checks;

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
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String typeAttr = element.getAttribute("type");
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        String text = element.getTextContent().trim();
        if (!text.startsWith("@")) {
            return;
        }

        String refType = parseResourceType(text);
        if (refType != null && !refType.equals(typeAttr)) {
            String message = String.format(
                    "Resource alias type `%1$s` does not match referenced resource type `%2$s`",
                    typeAttr, refType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String parseResourceType(String reference) {
        String s = reference;
        if (s.startsWith("@+")) {
            s = s.substring(2);
        } else if (s.startsWith("@")) {
            s = s.substring(1);
        } else {
            return null;
        }

        int colonIndex = s.indexOf(':');
        if (colonIndex != -1) {
            s = s.substring(colonIndex + 1);
        }

        int slashIndex = s.indexOf('/');
        if (slashIndex != -1) {
            return s.substring(0, slashIndex);
        }
        return null;
    }
}