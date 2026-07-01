package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STYLE;

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
            Severity.FATAL,
            new Implementation(
                    ResourcePrefixDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER)
            )
    );

    private static final String[] VALUE_TAGS = {
            "string", "string-array", "plurals",
            "drawable", "dimen", "color", "bool",
            "integer", "integer-array", "fraction",
            "array", "declare-styleable", "attr",
            TAG_STYLE, TAG_ITEM
    };

    public ResourcePrefixDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.MENU
                || folderType == ResourceFolderType.XML
                || folderType == ResourceFolderType.ANIM
                || folderType == ResourceFolderType.ANIMATOR
                || folderType == ResourceFolderType.COLOR
                || folderType == ResourceFolderType.RAW
                || folderType == ResourceFolderType.INTERPOLATOR
                || folderType == ResourceFolderType.TRANSITION
                || folderType == ResourceFolderType.FONT;
    }

    @Nullable
    private String getResourcePrefix(@NonNull XmlContext context) {
        return context.getProject().getResourcePrefix();
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String prefix = getResourcePrefix(context);
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            // Values files: check individual resource name attributes
            checkValueFile(context, document, prefix);
        } else {
            // Non-values files: the file name itself is the resource name
            checkFileResource(context, prefix, folderType);
        }
    }

    private void checkFileResource(@NonNull XmlContext context,
            @NonNull String prefix,
            @NonNull ResourceFolderType folderType) {
        File file = context.file;
        String fileName = file.getName();
        // Strip extension
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (!resourceName.startsWith(prefix)) {
            String message = String.format(
                    "Resource named '`%1$s`' does not start with the project's resource prefix "
                            + "'`%2$s`'; rename to '`%3$s`'",
                    resourceName,
                    prefix,
                    prefix + resourceName);
            Location location = Location.create(file);
            context.report(ISSUE, location, message);
        }
    }

    private void checkValueFile(@NonNull XmlContext context,
            @NonNull Document document,
            @NonNull String prefix) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String rootTag = root.getTagName();
        if (!TAG_RESOURCES.equals(rootTag)) {
            return;
        }

        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            checkValueElement(context, element, prefix);
        }
    }

    private void checkValueElement(@NonNull XmlContext context,
            @NonNull Element element,
            @NonNull String prefix) {
        String tag = element.getTagName();

        // For <item> elements, we need to look at the type attribute
        if (TAG_ITEM.equals(tag)) {
            String type = element.getAttribute(ATTR_TYPE);
            if (type == null || type.isEmpty()) {
                // No type attribute, skip
                return;
            }
        }

        // Get the name attribute
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Skip names that start with "android:" namespace
        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
            return;
        }

        // For attr elements inside declare-styleable, they may be prefixed differently
        // We skip checking child attrs of declare-styleable if the parent is already checked
        // Actually we check all top-level resources

        if (!name.startsWith(prefix)) {
            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            Location location;
            if (nameAttr != null) {
                location = context.getLocation(nameAttr);
            } else {
                location = context.getLocation(element);
            }

            String message = String.format(
                    "Resource named '`%1$s`' does not start with the project's resource prefix "
                            + "'`%2$s`'; rename to '`%3$s`'",
                    name,
                    prefix,
                    prefix + name);
            context.report(ISSUE, element, location, message);
        }

        // Also check children for declare-styleable attrs
        if ("declare-styleable".equals(tag)) {
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    checkValueElement(context, (Element) child, prefix);
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null; // We use visitDocument instead
    }
}