package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends ResourceXmlDetector {

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
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            String fileName = context.file.getName();
            String baseName = Lint.getBaseName(fileName);
            if (!hasPrefix(baseName, prefix)) {
                String message = String.format(
                    "Resource file's name `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s`?",
                    baseName, prefix, camelToUnderline(prefix) + baseName
                );
                context.report(ISSUE, context.getLocation(document), message);
            }
        } else if (document.getDocumentElement() != null) {
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    String name = element.getAttribute("name");
                    if (name != null && !name.isEmpty()) {
                        if (!hasPrefix(name, prefix)) {
                            String message = String.format(
                                "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s`?",
                                name, prefix, camelToUnderline(prefix) + name
                            );
                            context.report(ISSUE, element, context.getNameLocation(element), message);
                        }
                    }
                }
            }
        }
    }

    public static boolean hasPrefix(@NonNull String name, @NonNull String prefix) {
        if (name.startsWith(prefix)) {
            return true;
        }

        if (name.regionMatches(true, 0, prefix, 0, prefix.length())) {
            if (name.length() == prefix.length()) {
                return true;
            }
            char next = name.charAt(prefix.length());
            if (next == '_' || Character.isUpperCase(next)) {
                return true;
            }
        }

        return false;
    }

    @NonNull
    public static String camelToUnderline(@NonNull String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append('_');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}