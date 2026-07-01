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
import java.util.Collection;
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
        return java.util.Collections.singletonList("name");
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
        if (!"name".equals(attribute.getName())) {
            return;
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }
        org.w3c.dom.Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                return;
            }
        }

        String tagName = element.getTagName();
        String aliasType;
        if ("item".equals(tagName)) {
            aliasType = element.getAttribute("type");
            if (aliasType.isEmpty()) {
                return;
            }
        } else {
            aliasType = tagName;
        }

        String textContent = element.getTextContent();
        if (textContent == null) {
            return;
        }
        String value = textContent.trim();
        if (!value.startsWith("@")) {
            return;
        }

        String targetType = getResourceType(value);
        if (targetType == null) {
            return;
        }

        if (!aliasType.equals(targetType)) {
            String message = String.format(
                    "The resource alias '%s' is of type '%s' but points to a '%s' resource",
                    attribute.getValue(), aliasType, targetType);
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    message);
        }
    }

    private static String getResourceType(String val) {
        if (val == null || !val.startsWith("@")) {
            return null;
        }
        int start = 1;
        if (val.startsWith("@+")) {
            start = 2;
        } else if (val.startsWith("@*")) {
            start = 2;
        }
        int slash = val.indexOf('/');
        if (slash == -1) {
            return null;
        }
        int colon = val.indexOf(':', start);
        if (colon != -1 && colon < slash) {
            start = colon + 1;
        }
        return val.substring(start, slash);
    }
}