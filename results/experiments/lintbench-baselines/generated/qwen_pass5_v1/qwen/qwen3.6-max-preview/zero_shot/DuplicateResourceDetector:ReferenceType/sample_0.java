package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
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
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String aliasType = element.getAttribute("type");
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        String reference = element.getTextContent().trim();
        if (reference.isEmpty() || !reference.startsWith("@")) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(reference);
        if (url == null || url.type == null) {
            return;
        }

        String refType = url.type.getName();
        if (!aliasType.equals(refType)) {
            String message = String.format(
                    "Resource alias type `%1$s` does not match referenced resource type `%2$s`",
                    aliasType, refType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}