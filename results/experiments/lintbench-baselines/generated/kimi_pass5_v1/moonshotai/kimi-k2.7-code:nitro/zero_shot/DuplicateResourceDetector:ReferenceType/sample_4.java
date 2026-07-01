package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends ResourceXmlDetector {
    public static final Issue REFERENCE_TYPE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent == element.getOwnerDocument()) {
            checkRootAlias(context, element);
        } else if (parent != null && "resources".equals(parent.getNodeName())) {
            checkValueAlias(context, element);
        }
    }

    private static void checkRootAlias(XmlContext context, Element element) {
        String tag = element.getTagName();
        ResourceType aliasType = ResourceType.getEnum(tag);
        if (aliasType == null) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
            Attr attr = (Attr) attributes.item(i);
            if (isNamespaceDeclaration(attr)) {
                continue;
            }

            String localName = getLocalName(attr);
            if (!localName.equals(tag)) {
                continue;
            }

            String value = attr.getValue();
            if (!isSingleResourceReference(value)) {
                continue;
            }

            ResourceUrl url = ResourceUrl.parse(value.trim());
            if (url != null && url.type != null && url.type != aliasType) {
                context.report(
                        REFERENCE_TYPE,
                        attr,
                        context.getLocation(attr),
                        String.format(
                                "Expected reference of type '%1$s' but got '%2$s'",
                                aliasType.getName(), url.type.getName()));
            }
        }
    }

    private static void checkValueAlias(XmlContext context, Element element) {
        String tag = element.getTagName();
        ResourceType aliasType;
        if ("item".equals(tag)) {
            String typeAttr = element.getAttribute("type");
            if (typeAttr.isEmpty()) {
                return;
            }
            aliasType = ResourceType.getEnum(typeAttr);
        } else {
            aliasType = ResourceType.getEnum(tag);
        }

        if (aliasType == null || hasElementChildren(element)) {
            return;
        }

        String value = element.getTextContent();
        if (!isSingleResourceReference(value)) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value.trim());
        if (url != null && url.type != null && url.type != aliasType) {
            context.report(
                    REFERENCE_TYPE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Expected reference of type '%1$s' but got '%2$s'",
                            aliasType.getName(), url.type.getName()));
        }
    }

    private static boolean isNamespaceDeclaration(Attr attr) {
        String name = attr.getName();
        return name.equals("xmlns") || name.startsWith("xmlns:");
    }

    private static String getLocalName(Attr attr) {
        String localName = attr.getLocalName();
        if (localName != null) {
            return localName;
        }
        String name = attr.getName();
        int colon = name.indexOf(':');
        return colon == -1 ? name : name.substring(colon + 1);
    }

    private static boolean isSingleResourceReference(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("@")) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasElementChildren(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
            child = child.getNextSibling();
        }
        return false;
    }
}