package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    ResourcePrefixDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        File file = context.file;
        String name = getBaseName(file.getName());
        if (!name.startsWith(prefix)) {
            context.report(
                    ISSUE,
                    Location.create(file),
                    String.format("Resource named '%s' does not start with the project's resource prefix '%s'", name, prefix)
            );
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            String name = getBaseName(context.file.getName());
            if (!name.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        String.format("Resource named '%s' does not start with the project's resource prefix '%s'", name, prefix)
                );
            }
        } else if (folderType == ResourceFolderType.VALUES) {
            Element root = document.getDocumentElement();
            if (root != null && "resources".equals(root.getTagName())) {
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) child;
                        Attr nameAttr = element.getAttributeNode("name");
                        if (nameAttr != null) {
                            checkValueResource(context, element, nameAttr, prefix);
                        }
                    }
                }
            }
        }
    }

    private void checkValueResource(@NonNull XmlContext context, @NonNull Element element, @NonNull Attr nameAttr, @NonNull String prefix) {
        String name = nameAttr.getValue();
        String tagName = element.getTagName();

        boolean isStyle = "style".equals(tagName) || "declare-styleable".equals(tagName);
        if (isStyle) {
            if (!matchesStylePrefix(name, prefix)) {
                context.report(
                        ISSUE,
                        nameAttr,
                        context.getLocation(nameAttr),
                        String.format("Style resource named '%s' does not start with the project's resource prefix '%s' (or its camelCase equivalent)", name, prefix)
                );
            }
        } else {
            if (!name.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        nameAttr,
                        context.getLocation(nameAttr),
                        String.format("Resource named '%s' does not start with the project's resource prefix '%s'", name, prefix)
                );
            }
        }

        if ("declare-styleable".equals(tagName)) {
            NodeList attrs = element.getChildNodes();
            for (int j = 0; j < attrs.getLength(); j++) {
                Node attrNode = attrs.item(j);
                if (attrNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element attrElement = (Element) attrNode;
                    if ("attr".equals(attrElement.getTagName())) {
                        Attr attrNameAttr = attrElement.getAttributeNode("name");
                        if (attrNameAttr != null) {
                            String attrName = attrNameAttr.getValue();
                            if (!attrName.contains(":") && !attrName.startsWith(prefix)) {
                                context.report(
                                        ISSUE,
                                        attrNameAttr,
                                        context.getLocation(attrNameAttr),
                                        String.format("Attribute '%s' does not start with the project's resource prefix '%s'", attrName, prefix)
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        if (SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI()) && "id".equals(attribute.getLocalName())) {
            String value = attribute.getValue();
            String idName = null;
            if (value.startsWith(SdkConstants.NEW_ID_PREFIX)) {
                idName = value.substring(SdkConstants.NEW_ID_PREFIX.length());
            } else if (value.startsWith(SdkConstants.ID_PREFIX)) {
                idName = value.substring(SdkConstants.ID_PREFIX.length());
            }

            if (idName != null && !idName.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        String.format("ID '%s' does not start with the project's resource prefix '%s'", idName, prefix)
                );
            }
        }
    }

    private static boolean matchesStylePrefix(String name, String prefix) {
        if (name.startsWith(prefix)) {
            return true;
        }
        String camel = toCamelCase(prefix);
        if (camel.isEmpty()) {
            return false;
        }
        String camelUpper = Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
        String camelLower = Character.toLowerCase(camel.charAt(0)) + camel.substring(1);
        return name.startsWith(camelUpper) || name.startsWith(camelLower);
    }

    private static String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.indexOf('.');
        return dot == -1 ? fileName : fileName.substring(0, dot);
    }
}