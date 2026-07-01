package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
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
        if (!element.hasAttribute("type")) {
            return;
        }
        String aliasType = element.getAttribute("type");
        if (aliasType.isEmpty()) {
            return;
        }

        NodeList children = element.getChildNodes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getNodeValue());
            }
        }
        String value = sb.toString().trim();
        if (value.isEmpty()) {
            return;
        }

        String refType = getResourceType(value);
        if (refType != null) {
            if (!aliasType.equals(refType)) {
                if ("drawable".equals(aliasType) && "color".equals(refType)) {
                    return;
                }
                String message = String.format(
                        "Mismatch: alias is of type `%s` but points to `%s`",
                        aliasType, refType
                );
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    private static String getResourceType(String ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        if (ref.equals("@null")) {
            return null;
        }
        if (ref.startsWith("@") || ref.startsWith("?")) {
            int slash = ref.indexOf('/');
            if (slash != -1) {
                int start = 1;
                int colon = ref.indexOf(':');
                if (colon != -1 && colon < slash) {
                    start = colon + 1;
                }
                if (ref.charAt(start) == '+') {
                    start++;
                }
                return ref.substring(start, slash);
            }
        }
        return null;
    }
}