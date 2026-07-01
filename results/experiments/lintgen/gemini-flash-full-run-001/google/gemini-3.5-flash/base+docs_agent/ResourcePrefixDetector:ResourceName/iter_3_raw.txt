package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import java.io.File;
import java.util.EnumSet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
            )
    );

    private static String getResourcePrefix(Project project) {
        if (project == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = project.getClass().getMethod("getResourcePrefix");
            return (String) method.invoke(project);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean libraryPrefixMatches(String prefix, String name) {
        if (name.startsWith(prefix)) {
            return true;
        }
        int prefixLength = prefix.length();
        if (prefixLength > 0 && prefix.charAt(prefixLength - 1) == '_') {
            if (name.length() == prefixLength - 1 && prefix.startsWith(name)) {
                return true;
            }
        }
        return false;
    }

    public static String camelCaseToUnderlines(String string) {
        if (string == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
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

    public static String underlinesToCamelCase(String string) {
        if (string == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean nextUpperCase = false;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    sb.append(Character.toUpperCase(c));
                    nextUpperCase = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static boolean matchesPrefix(String name, String prefix) {
        return libraryPrefixMatches(prefix, name)
                || libraryPrefixMatches(camelCaseToUnderlines(prefix), name)
                || libraryPrefixMatches(underlinesToCamelCase(prefix), name);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
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
            String baseName = getBaseName(fileName);
            if (!matchesPrefix(baseName, prefix)) {
                context.report(
                        ISSUE,
                        Location.create(context.file),
                        "The resource name `" + baseName + "` must begin with the prefix `" + prefix + "`"
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
                            if (!matchesPrefix(name, prefix)) {
                                context.report(
                                        ISSUE,
                                        element,
                                        context.getLocation(element),
                                        "The resource name `" + name + "` must begin with the prefix `" + prefix + "`"
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        String prefix = getResourcePrefix(context.getProject());
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        String fileName = context.file.getName();
        String baseName = getBaseName(fileName);
        if (!matchesPrefix(baseName, prefix)) {
            context.report(
                    ISSUE,
                    Location.create(context.file),
                    "The resource name `" + baseName + "` must begin with the prefix `" + prefix + "`"
            );
        }
    }

    private static String getBaseName(String fileName) {
        int dot = fileName.indexOf('.');
        return dot != -1 ? fileName.substring(0, dot) : fileName;
    }
}