package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.SdkUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TAG_ITEM;

/**
 * Checks that resources in Gradle projects which specify a resource prefix
 * actually use that prefix for all resources.
 */
public class ResourcePrefixDetector extends ResourceXmlDetector {

    /** The main issue surfaced by this detector */
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
                    EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE)));

    /** Constructs a new {@link ResourcePrefixDetector} */
    public ResourcePrefixDetector() {
    }

    // ---- Implements XmlDetector ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = getResourcePrefix(context);
        if (prefix == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            // Manifest file
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            // For value files, check each item's name attribute
            checkValueFile(context, document, prefix);
        } else {
            // For non-value resource files, check the filename itself
            checkResourceFileName(context, prefix, folderType);
        }
    }

    /**
     * Returns the resource prefix configured for this project, or null if none is configured
     * or this is not a Gradle project.
     */
    @Nullable
    private static String getResourcePrefix(@NonNull XmlContext context) {
        Project project = context.getProject();
        if (project == null) {
            return null;
        }
        return project.getResourcePrefix();
    }

    /**
     * Checks a value file: iterates over all items and checks that each
     * resource name starts with the required prefix.
     */
    private static void checkValueFile(@NonNull XmlContext context,
            @NonNull Document document,
            @NonNull String prefix) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            checkElement(context, element, prefix);
        }
    }

    /**
     * Checks a single element in a values file for a proper resource prefix.
     */
    private static void checkElement(@NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String prefix) {
        String tag = element.getTagName();

        // For <item> elements, the type is specified via the "type" attribute
        // For other elements (string, dimen, color, etc.), the tag itself is the type
        // Either way, we want to check the "name" attribute
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Skip tools: attributes and android: prefixed names
        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
            return;
        }

        if (!name.startsWith(prefix)) {
            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            Location location;
            if (nameAttr != null) {
                location = context.getLocation(nameAttr);
            } else {
                location = context.getLocation(element);
            }
            context.report(ISSUE, element, location,
                    String.format("Resource named '`%1$s`' does not start with the project's resource prefix '`%2$s`'; rename to '`%3$s`'",
                            name, prefix, prefix + name));
        }

        // Recurse into nested elements (e.g. declare-styleable children)
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, prefix);
            }
        }
    }

    /**
     * Checks a non-value resource file: the filename (minus extension) must start with prefix.
     */
    private static void checkResourceFileName(@NonNull XmlContext context,
            @NonNull String prefix,
            @NonNull ResourceFolderType folderType) {
        File file = context.file;
        String fileName = file.getName();

        // Strip the extension
        int dot = fileName.lastIndexOf('.');
        String resourceName = (dot != -1) ? fileName.substring(0, dot) : fileName;

        if (!resourceName.startsWith(prefix)) {
            Location location = Location.create(file);
            context.report(ISSUE, location,
                    String.format("Resource named '`%1$s`' does not start with the project's resource prefix '`%2$s`'; rename to '`%3$s`'",
                            resourceName, prefix, prefix + resourceName));
        }
    }
}