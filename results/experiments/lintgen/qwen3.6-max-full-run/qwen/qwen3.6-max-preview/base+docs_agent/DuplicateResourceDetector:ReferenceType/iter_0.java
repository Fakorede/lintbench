package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }

        String aliasType = element.getAttribute("type");
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        Node child = element.getFirstChild();
        if (child == null || child.getNodeType() != Node.TEXT_NODE) {
            return;
        }

        String text = child.getNodeValue().trim();
        if (!text.startsWith("@")) {
            return;
        }

        String refType = extractResourceType(text);
        if (refType == null) {
            return;
        }

        if (!aliasType.equalsIgnoreCase(refType)) {
            String message = String.format(
                    "Resource alias type `%1$s` does not match referenced resource type `%2$s`",
                    aliasType, refType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String extractResourceType(String reference) {
        String ref = reference.startsWith("@+") ? reference.substring(2) : reference.substring(1);
        int colonIndex = ref.indexOf(':');
        if (colonIndex != -1) {
            ref = ref.substring(colonIndex + 1);
        }
        int slashIndex = ref.indexOf('/');
        if (slashIndex != -1) {
            return ref.substring(0, slashIndex);
        }
        return null;
    }
}