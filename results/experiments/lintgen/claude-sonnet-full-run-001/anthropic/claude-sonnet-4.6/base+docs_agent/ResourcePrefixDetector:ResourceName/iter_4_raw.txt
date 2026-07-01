package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ITEM;

public class ResourcePrefixDetector extends Detector implements XmlScanner, ResourceFolderScanner {

    public static final Issue ISSUE = Issue.create(
            "ResourceName",
            "Resource with Wrong Prefix",
            "In Gradle projects you can specify a resource prefix that all resources " +
            "in the project must conform to. This makes it easier to ensure that you don't " +
            "accidentally combine resources from different libraries, since they all end " +
            "up in the same shared app namespace.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER)
            )
    );

    public ResourcePrefixDetector() {
    }

    // ---- Implements ResourceFolderScanner ----

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        String prefix = getResourcePrefix(context);
        if (prefix == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File file = context.file;
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!resourceName.startsWith(prefix)) {
            String message = String.format(
                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; "
                            + "rename to `%3$s`?",
                    resourceName, prefix, prefix + resourceName);
            Location location = Location.create(file);
            context.report(ISSUE, location, message);
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = getResourcePrefix(context);
        if (prefix == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            // For non-values files, check the file name itself
            File file = context.file;
            String fileName = file.getName();
            int dotIndex = fileName.lastIndexOf('.');
            String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

            if (!resourceName.startsWith(prefix)) {
                String message = String.format(
                        "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; "
                                + "rename to `%3$s`?",
                        resourceName, prefix, prefix + resourceName);
                context.report(ISSUE, Location.create(file), message);
            }
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        checkValueChildren(context, root, prefix);
    }

    private void checkValueChildren(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String prefix) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                checkValueElement(context, childElement, prefix);
            }
        }
    }

    private void checkValueElement(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String prefix) {
        String tagName = element.getTagName();

        String name = element.getAttribute(ATTR_NAME);
        if (name != null && !name.isEmpty()) {
            ResourceType type = getResourceType(tagName);
            if (type != null) {
                if (!name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                    if (!name.startsWith(prefix)) {
                        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
                        Location location = nameAttr != null
                                ? context.getValueLocation(nameAttr)
                                : context.getLocation(element);
                        String message = String.format(
                                "Resource named `%1$s` does not start with the project's resource "
                                        + "prefix `%2$s`; rename to `%3$s`?",
                                name, prefix, prefix + name);
                        context.report(ISSUE, element, location, message);
                    }
                }
            }
        }

        if (TAG_ITEM.equals(tagName)) {
            return;
        }
        checkValueChildren(context, element, prefix);
    }

    @Nullable
    private static String getResourcePrefix(@NonNull Context context) {
        Project project = context.getProject();
        if (project.isGradleProject()) {
            com.android.tools.lint.detector.api.Project gradleProject = project;
            // Try to get the resource prefix via the gradle variant
            try {
                // Use reflection or direct API if available
                java.lang.reflect.Method method = project.getClass().getMethod("getResourcePrefix");
                Object result = method.invoke(project);
                if (result instanceof String) {
                    return (String) result;
                }
            } catch (Exception e) {
                // Method not available, try alternative
            }
            // Try getBuildVariant approach
            try {
                java.lang.reflect.Method method = project.getClass().getMethod("getCurrentVariant");
                Object variant = method.invoke(project);
                if (variant != null) {
                    java.lang.reflect.Method prefixMethod = variant.getClass().getMethod("getResourcePrefix");
                    Object result = prefixMethod.invoke(variant);
                    if (result instanceof String) {
                        return (String) result;
                    }
                }
            } catch (Exception e) {
                // Not available
            }
        }
        return null;
    }

    @Nullable
    private static ResourceType getResourceType(@NonNull String tagName) {
        switch (tagName) {
            case "string":
                return ResourceType.STRING;
            case "string-array":
                return ResourceType.ARRAY;
            case "integer":
                return ResourceType.INTEGER;
            case "integer-array":
                return ResourceType.ARRAY;
            case "bool":
                return ResourceType.BOOL;
            case "color":
                return ResourceType.COLOR;
            case "dimen":
                return ResourceType.DIMEN;
            case "drawable":
                return ResourceType.DRAWABLE;
            case "style":
                return ResourceType.STYLE;
            case "array":
                return ResourceType.ARRAY;
            case "plurals":
                return ResourceType.PLURALS;
            case "fraction":
                return ResourceType.FRACTION;
            case "declare-styleable":
                return ResourceType.STYLEABLE;
            case "attr":
                return ResourceType.ATTR;
            case "id":
                return ResourceType.ID;
            case "layout":
                return ResourceType.LAYOUT;
            case "menu":
                return ResourceType.MENU;
            case "mipmap":
                return ResourceType.MIPMAP;
            case "raw":
                return ResourceType.RAW;
            case "xml":
                return ResourceType.XML;
            case TAG_ITEM:
                return ResourceType.STRING;
            default:
                return null;
        }
    }
}