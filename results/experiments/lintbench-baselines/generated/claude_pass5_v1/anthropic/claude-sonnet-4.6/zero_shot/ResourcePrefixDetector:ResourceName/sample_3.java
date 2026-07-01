package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;

/**
 * Checks that resources in Gradle projects conform to the specified resource prefix.
 */
public class ResourcePrefixDetector extends LayoutDetector implements XmlScanner, ResourceFolderScanner {

    /** The main issue discovered by this detector */
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

    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link ResourcePrefixDetector} */
    public ResourcePrefixDetector() {
    }

    // ---- Implements ResourceFolderScanner ----

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        String prefix = getResourcePrefix(context);
        if (prefix == null) {
            return;
        }

        // Check the folder name for resource types that are file-based (e.g. layout, drawable, etc.)
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        // For file-based resources, the file name itself is the resource name
        // We handle those in checkBinaryResource / visitDocument
    }

    // ---- Implements XmlScanner ----

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = getResourcePrefix(context);
        if (prefix == null) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            // For value resources, check the name attributes of resource declarations
            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }
            checkValueFile(context, root, prefix);
        } else {
            // For file-based resources, check the file name
            checkFileResource(context, prefix, folderType);
        }
    }

    /**
     * Checks a value resource file for resource names that don't conform to the prefix.
     */
    private void checkValueFile(@NonNull XmlContext context, @NonNull Element root,
            @NonNull String prefix) {
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
     * Checks a single element for a resource name attribute that conforms to the prefix.
     */
    private void checkElement(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String prefix) {
        String tagName = element.getTagName();

        // Handle <item> elements which use a "type" attribute
        // and regular resource elements like <string>, <dimen>, <color>, etc.
        if (tagName.equals("item") || isResourceElement(tagName)) {
            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            if (nameAttr != null) {
                String name = nameAttr.getValue();
                if (name != null && !name.isEmpty()) {
                    checkName(context, name, prefix, nameAttr);
                }
            }
        }

        // Also check nested elements (e.g. declare-styleable children)
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement(context, (Element) child, prefix);
            }
        }
    }

    /**
     * Checks whether the given tag name represents a resource declaration element.
     */
    private static boolean isResourceElement(@NonNull String tagName) {
        switch (tagName) {
            case "string":
            case "string-array":
            case "integer":
            case "integer-array":
            case "bool":
            case "dimen":
            case "color":
            case "array":
            case "style":
            case "declare-styleable":
            case "attr":
            case "plurals":
            case "fraction":
            case "drawable":
            case "layout":
            case "menu":
            case "anim":
            case "animator":
            case "interpolator":
            case "transition":
            case "raw":
            case "xml":
            case "font":
            case "navigation":
                return true;
            default:
                return false;
        }
    }

    /**
     * Checks a file-based resource (e.g. a layout file, drawable file, etc.) for
     * a name that conforms to the prefix.
     */
    private void checkFileResource(@NonNull XmlContext context, @NonNull String prefix,
            @NonNull ResourceFolderType folderType) {
        File file = context.file;
        String fileName = file.getName();

        // Strip the extension
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!resourceName.startsWith(prefix)) {
            String message = String.format(
                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; "
                            + "rename to `%3$s%1$s`?",
                    resourceName, prefix, prefix);

            Location location = Location.create(file);
            context.report(ISSUE, location, message);
        }
    }

    /**
     * Checks a resource name against the given prefix.
     */
    private void checkName(@NonNull XmlContext context, @NonNull String name,
            @NonNull String prefix, @NonNull Attr nameAttr) {
        // Skip names that are tool-generated or references
        if (name.startsWith("@") || name.startsWith("?")) {
            return;
        }

        // Strip any type qualifier (e.g. "layout/foo" -> "foo")
        int slashIndex = name.indexOf('/');
        String localName = slashIndex >= 0 ? name.substring(slashIndex + 1) : name;

        if (!localName.startsWith(prefix)) {
            String message = String.format(
                    "Resource named `%1$s` does not start with the project's resource prefix `%2$s`; "
                            + "rename to `%3$s%1$s`?",
                    localName, prefix, prefix);

            Location location = context.getLocation(nameAttr);
            context.report(ISSUE, nameAttr, location, message);
        }
    }

    /**
     * Returns the resource prefix for the given context's project, or null if none is specified.
     */
    @Nullable
    private static String getResourcePrefix(@NonNull Context context) {
        Project project = context.getProject();
        if (project == null) {
            return null;
        }
        String prefix = project.getResourcePrefix();
        if (prefix == null || prefix.isEmpty()) {
            return null;
        }
        return prefix;
    }
}