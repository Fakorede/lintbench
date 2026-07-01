package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ResourcePrefixDetector extends Detector implements Detector.XmlScanner, Detector.BinaryResourceScanner {

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
            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
        )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Nullable
    private String getResourcePrefix(@NonNull Project project) {
        return project.getResourcePrefix();
    }

    public static boolean libraryPrefixMatches(@NonNull String prefix, @NonNull String name) {
        if (name.startsWith(prefix)) {
            return true;
        }
        if (prefix.endsWith("_") && prefix.length() > 1 && name.equals(prefix.substring(0, prefix.length() - 1))) {
            return true;
        }
        return false;
    }

    @NonNull
    public static String camelCaseToUnderlines(@NonNull String string) {
        if (string.isEmpty()) {
            return string;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toLowerCase(string.charAt(0)));
        for (int i = 1; i < string.length(); i++) {
            char c = string.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @NonNull
    public static String underlinesToCamelCase(@NonNull String string) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
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

    private static boolean isPrefixed(@NonNull String name, @NonNull String prefix) {
        if (libraryPrefixMatches(prefix, name)) {
            return true;
        }
        String underlines = camelCaseToUnderlines(prefix);
        if (libraryPrefixMatches(underlines, name)) {
            return true;
        }
        String camelCase = underlinesToCamelCase(prefix);
        if (libraryPrefixMatches(camelCase, name)) {
            return true;
        }
        if (!camelCase.isEmpty() && Character.isLowerCase(camelCase.charAt(0))) {
            String capitalized = Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
            if (libraryPrefixMatches(capitalized, name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = getResourcePrefix(context.getProject());
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }
        if (folderType != ResourceFolderType.VALUES) {
            String fileName = context.file.getName();
            int dot = fileName.indexOf('.');
            String name = dot != -1 ? fileName.substring(0, dot) : fileName;
            if (!isPrefixed(name, prefix)) {
                context.report(
                    ISSUE,
                    Location.create(context.file),
                    "Resource named '" + name + "' does not start with the project's resource prefix '" + prefix + "'"
                );
            }
        } else {
            Element root = document.getDocumentElement();
            if (root != null && "resources".equals(root.getTagName())) {
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) child;
                        String name = element.getAttribute("name");
                        if (name != null && !name.isEmpty()) {
                            if (!isPrefixed(name, prefix)) {
                                Attr nameAttr = element.getAttributeNode("name");
                                Location location = nameAttr != null ? context.getLocation(nameAttr) : context.getLocation(element);
                                context.report(
                                    ISSUE,
                                    location,
                                    "Resource '" + name + "' does not start with the project's resource prefix '" + prefix + "'"
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        String prefix = getResourcePrefix(context.getProject());
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            String fileName = context.file.getName();
            int dot = fileName.indexOf('.');
            String name = dot != -1 ? fileName.substring(0, dot) : fileName;
            if (!isPrefixed(name, prefix)) {
                context.report(
                    ISSUE,
                    Location.create(context.file),
                    "Resource named '" + name + "' does not start with the project's resource prefix '" + prefix + "'"
                );
            }
        }
    }
}