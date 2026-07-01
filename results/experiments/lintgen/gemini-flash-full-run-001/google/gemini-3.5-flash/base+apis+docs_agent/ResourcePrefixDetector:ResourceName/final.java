package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NotNull;
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

    private static String getBaseName(String fileName) {
        int index = fileName.indexOf('.');
        return index == -1 ? fileName : fileName.substring(0, index);
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkBinaryResource(@NotNull ResourceContext context) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        File file = context.file;
        String name = getBaseName(file.getName());
        if (!matchesPrefix(name, prefix)) {
            context.report(
                    ISSUE,
                    Location.create(file),
                    String.format("Resource named '%s' does not start with the project's resource prefix '%s'", name, prefix)
            );
        }
    }

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        String prefix = context.getProject().getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            String name = getBaseName(context.file.getName());
            if (!matchesPrefix(name, prefix)) {
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
                            String name = nameAttr.getValue();
                            if (!matchesPrefix(name, prefix)) {
                                context.report(
                                        ISSUE,
                                        context.getLocation(nameAttr),
                                        String.format("Resource named '%s' does not start with the project's resource prefix '%s'", name, prefix)
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean matchesPrefix(@NotNull String name, @NotNull String prefix) {
        String underlines = camelCaseToUnderlines(prefix);
        String camelCase = underlinesToCamelCase(prefix);
        if (libraryPrefixMatches(underlines, name) || libraryPrefixMatches(camelCase, name)) {
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

    public static boolean libraryPrefixMatches(@NotNull String prefix, @NotNull String name) {
        if (name.startsWith(prefix)) {
            return true;
        }

        if (name.startsWith("Theme.")) {
            return name.regionMatches(6, prefix, 0, prefix.length());
        } else if (name.startsWith("Widget.")) {
            return name.regionMatches(7, prefix, 0, prefix.length());
        } else if (name.startsWith("TextAppearance.")) {
            return name.regionMatches(15, prefix, 0, prefix.length());
        } else if (name.startsWith("ThemeOverlay.")) {
            return name.regionMatches(13, prefix, 0, prefix.length());
        } else if (name.startsWith("Base.")) {
            if (name.regionMatches(5, prefix, 0, prefix.length())) {
                return true;
            }
            if (name.startsWith("Base.Theme.")) {
                return name.regionMatches(11, prefix, 0, prefix.length());
            } else if (name.startsWith("Base.Widget.")) {
                return name.regionMatches(12, prefix, 0, prefix.length());
            } else if (name.startsWith("Base.TextAppearance.")) {
                return name.regionMatches(20, prefix, 0, prefix.length());
            } else if (name.startsWith("Base.ThemeOverlay.")) {
                return name.regionMatches(18, prefix, 0, prefix.length());
            }
        }

        return false;
    }

    @NotNull
    public static String camelCaseToUnderlines(@NotNull String string) {
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

    @NotNull
    public static String underlinesToCamelCase(@NotNull String string) {
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
}