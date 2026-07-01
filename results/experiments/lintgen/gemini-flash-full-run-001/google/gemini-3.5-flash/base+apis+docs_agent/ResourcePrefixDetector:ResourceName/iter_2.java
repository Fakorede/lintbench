package com.android.tools.lint.checks;

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

    @Nullable
    private String getResourcePrefix(@NonNull Context context) {
        Project project = context.getProject();
        return project.getResourcePrefix();
    }

    private static String getBaseName(String fileName) {
        int index = fileName.indexOf('.');
        return index == -1 ? fileName : fileName.substring(0, index);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        String prefix = getResourcePrefix(context);
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
        String prefix = getResourcePrefix(context);
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
    }

    public static boolean matchesStylePrefix(@NonNull String name, @NonNull String prefix) {
        if (name.startsWith(prefix)) {
            return true;
        }
        String camelCase = toCamelCase(prefix);
        if (name.startsWith(camelCase)) {
            return true;
        }
        if (!camelCase.isEmpty()) {
            String capitalized = Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
            if (name.startsWith(capitalized)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String toCamelCase(@NonNull String prefix) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
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
}