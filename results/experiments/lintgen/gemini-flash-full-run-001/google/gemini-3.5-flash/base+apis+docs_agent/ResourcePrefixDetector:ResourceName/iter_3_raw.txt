package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    private String getResourcePrefix(@NotNull Context context) {
        Project project = context.getProject();
        try {
            java.lang.reflect.Method method = project.getClass().getMethod("getResourcePrefix");
            return (String) method.invoke(project);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Method method = project.getClass().getMethod("getGradleProjectModel");
                Object gradleProject = method.invoke(project);
                if (gradleProject != null) {
                    java.lang.reflect.Method getPrefix = gradleProject.getClass().getMethod("getResourcePrefix");
                    return (String) getPrefix.invoke(gradleProject);
                }
            } catch (Throwable t2) {
                try {
                    java.lang.reflect.Method method = project.getClass().getMethod("getGradleProject");
                    Object gradleProject = method.invoke(project);
                    if (gradleProject != null) {
                        java.lang.reflect.Method getPrefix = gradleProject.getClass().getMethod("getResourcePrefix");
                        return (String) getPrefix.invoke(gradleProject);
                    }
                } catch (Throwable t3) {
                    // ignore
                }
            }
        }
        return null;
    }

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
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
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

    private void checkValueResource(@NotNull XmlContext context, @NotNull Element element, @NotNull Attr nameAttr, @NotNull String prefix) {
        String name = nameAttr.getValue();
        String tagName = element.getTagName();

        boolean isStyle = "style".equals(tagName) || "declare-styleable".equals(tagName);
        if (isStyle) {
            if (!matchesStylePrefix(name, prefix)) {
                context.report(
                        ISSUE,
                        context.getLocation(nameAttr),
                        String.format("Style resource named '%s' does not start with the project's resource prefix '%s' (or its camelCase equivalent)", name, prefix)
                );
            }
        } else {
            if (!name.startsWith(prefix)) {
                context.report(
                        ISSUE,
                        context.getLocation(nameAttr),
                        String.format("Resource named '%s' does not start with the project's resource prefix '%s'", name, prefix)
                );
            }
        }
    }

    public static boolean matchesStylePrefix(@NotNull String name, @NotNull String prefix) {
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

    @NotNull
    private static String toCamelCase(@NotNull String prefix) {
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