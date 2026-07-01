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
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;

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
                    EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER)
            )
    );

    private String mPrefix;

    public ResourcePrefixDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mPrefix == null) {
            mPrefix = context.getProject().getResourcePrefix();
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (mPrefix == null) {
            return;
        }

        String prefix = mPrefix;
        ResourceFolderType folderType = context.getResourceFolderType();

        if (folderType == null) {
            // This is likely the manifest; skip it
            return;
        }

        if (folderType != ResourceFolderType.VALUES) {
            // For non-value resource files, the resource name is the filename itself
            String fileName = context.file.getName();
            if (fileName.endsWith(DOT_XML)) {
                fileName = fileName.substring(0, fileName.length() - DOT_XML.length());
            }
            // Strip qualifier suffix (e.g. "-hdpi") from folder name
            if (!fileName.startsWith(prefix)) {
                String message = String.format(
                        "Resource named '`%1$s`' does not start with the project's resource prefix '`%2$s`'; rename to '`%3$s`'",
                        fileName, prefix, prefix + fileName);
                Location location = Location.create(context.file);
                context.report(ISSUE, location, message);
            }
        } else {
            // For value files, check each resource name defined within
            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }
            checkValueChildren(context, root, prefix);
        }
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
        String tag = element.getTagName();

        // Determine if this element defines a named resource
        ResourceType type = ResourceType.fromXmlTag(element);
        if (type == null) {
            // Check for <item> tag with a type attribute
            if (TAG_ITEM.equals(tag)) {
                String typeAttr = element.getAttribute("type");
                if (typeAttr != null && !typeAttr.isEmpty()) {
                    type = ResourceType.fromClassName(typeAttr);
                }
            }
        }

        if (type != null) {
            String name = element.getAttribute(ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                // Skip resources that start with "android:" prefix
                if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
                    return;
                }
                if (!name.startsWith(prefix)) {
                    String suggested = prefix + name;
                    String message = String.format(
                            "Resource named '`%1$s`' does not start with the project's resource prefix '`%2$s`'; rename to '`%3$s`'",
                            name, prefix, suggested);
                    Attr nameAttr = element.getAttributeNode(ATTR_NAME);
                    Location location;
                    if (nameAttr != null) {
                        location = context.getLocation(nameAttr);
                    } else {
                        location = context.getLocation(element);
                    }
                    context.report(ISSUE, element, location, message);
                }
            }
        }

        // Recurse into children (e.g. for <declare-styleable> which contains <attr> children)
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkValueElement(context, (Element) child, prefix);
            }
        }
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        // We handle resource naming via visitDocument for XML files
        // and via checkBinaryResource for non-XML files (images, etc.)
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null) {
            mPrefix = context.getProject().getResourcePrefix();
        }
        if (mPrefix == null) {
            return;
        }

        String prefix = mPrefix;
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        String fileName = context.file.getName();
        // Remove extension
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            fileName = fileName.substring(0, dotIndex);
        }

        if (!fileName.startsWith(prefix)) {
            String message = String.format(
                    "Resource named '`%1$s`' does not start with the project's resource prefix '`%2$s`'; rename to '`%3$s`'",
                    fileName, prefix, prefix + fileName);
            Location location = Location.create(context.file);
            context.report(ISSUE, location, message);
        }
    }
}