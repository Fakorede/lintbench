package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String tagName = element.getTagName();
        String resourceType;
        if ("item".equals(tagName)) {
            resourceType = element.getAttribute("type");
            if (resourceType == null || resourceType.isEmpty()) {
                return;
            }
        } else {
            resourceType = tagName;
        }

        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        if (!value.startsWith("@") || value.startsWith("@null")) {
            return;
        }

        int slash = value.indexOf('/');
        if (slash == -1) {
            return;
        }

        String refType = value.substring(1, slash);
        if (refType.startsWith("+")) {
            refType = refType.substring(1);
        }
        int colon = refType.indexOf(':');
        if (colon != -1) {
            refType = refType.substring(colon + 1);
        }

        if (!resourceType.equals(refType)) {
            String message = String.format(
                    "Mismatching resource member types: alias is of type `%s` but references `%s` of type `%s`",
                    resourceType, value, refType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}