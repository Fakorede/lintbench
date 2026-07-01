package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Project;
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
        Project project = context.getProject();
        if (project == null) {
            return;
        }
        String prefix = project.getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            String fileName = context.file.getName();
            String baseName = Lint.getBaseName(fileName);
            if (!libraryPrefixMatches(prefix, baseName)) {
                String message = String.format(
                    "Resource file's name `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s`?",
                    baseName, prefix, camelCaseToUnderlines(prefix) + baseName
                );
                context.report(ISSUE, context.getLocation(document), message);
            }
        } else {
            Element root = document.getDocumentElement();
            if (root != null && "resources".equals(root.getTagName())) {
                NodeList children = root.getChildNodes();
                for (int i = 0, n = children.getLength(); i < n; i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) child;
                        String name = element.getAttribute("name");
                        if (name != null && !name.isEmpty()) {
                            if (!libraryPrefixMatches(prefix, name)) {
                                String message = String.format(
                                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; rename to `%3$s`?",
                                    name, prefix, camelCaseToUnderlines(prefix) + name
                                );
                                context.report(ISSUE, element, context.getNameLocation(element), message);
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean libraryPrefixMatches(@NonNull String prefix, @NonNull String name) {
        if (name.startsWith(prefix)) {
            return true;
        }

        int prefixLength = prefix.length();
        if (name.regionMatches(true, 0, prefix, 0, prefixLength)) {
            if (name.length() == prefixLength) {
                return true;
            }
            char next = name.charAt(prefixLength);
            if (next == '_' || Character.isUpperCase(next)) {
                return true;
            }
        }

        return false;
    }

    @NonNull
    public static String camelCaseToUnderlines(@NonNull String string) {
        if (string.isEmpty()) {
            return string;
        }

        StringBuilder sb = new StringBuilder(string.length() * 2);
        for (int i = 0, n = string.length(); i < n; i++) {
            char c = string.charAt(i);
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    @NonNull
    public static String underlinesToCamelCase(@NonNull String string) {
        StringBuilder sb = new StringBuilder(string.length());
        boolean nextUpper = false;
        for (int i = 0, n = string.length(); i < n; i++) {
            char c = string.charAt(i);
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}