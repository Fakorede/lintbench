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

/**
 * Checks that resources in Gradle projects conform to the specified resource prefix.
 */
public class ResourcePrefixDetector extends ResourceXmlDetector {

    /** The main issue: resource does not start with the required prefix */
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

    /** Constructs a new {@link ResourcePrefixDetector} */
    public ResourcePrefixDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
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

        // For files in a resource folder, check the file name itself (e.g. layout files,
        // drawable files, etc.)
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            // The resource name is the file name without extension
            String fileName = context.file.getName();
            int dotIndex = fileName.lastIndexOf('.');
            String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

            if (!resourceName.startsWith(prefix)) {
                String message = getErrorMessage(resourceName, prefix);
                Location location = Location.create(context.file);
                context.report(ISSUE, document.getDocumentElement(), location, message);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String prefix = getResourcePrefix(context);
        if (prefix == null || prefix.isEmpty()) {
            return;
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.VALUES) {
            // Non-value resources are handled by visitDocument (file name check)
            return;
        }

        // For value resources, check the name attribute
        String tag = element.getTagName();
        if (tag == null) {
            return;
        }

        // Determine resource type from tag
        ResourceType type = ResourceType.fromXmlTag(element);
        if (type == null) {
            // Also handle <item> tags with a type attribute
            if (TAG_ITEM.equals(tag)) {
                String typeAttr = element.getAttribute("type");
                if (typeAttr != null && !typeAttr.isEmpty()) {
                    type = ResourceType.fromClassName(typeAttr);
                }
            }
        }

        if (type == null) {
            return;
        }

        // Skip attr declarations inside declare-styleable - the styleable itself is checked
        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            String parentTag = ((Element) parentNode).getTagName();
            if ("declare-styleable".equals(parentTag) && "attr".equals(tag)) {
                return;
            }
        }

        String nameValue = element.getAttribute(ATTR_NAME);
        if (nameValue == null || nameValue.isEmpty()) {
            return;
        }

        // Strip off any style parent reference (e.g. "ParentStyle.MyStyle" -> check "MyStyle")
        // Actually for most resources we check the full name
        // For styles, the name might contain a dot-separated parent; check the last segment
        String resourceName = nameValue;

        // Remove any package prefix like "android:" or similar
        int colonIndex = resourceName.indexOf(':');
        if (colonIndex >= 0) {
            // This is referencing something in another package; skip
            return;
        }

        if (!resourceName.startsWith(prefix)) {
            String message = getErrorMessage(resourceName, prefix);
            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            Location location;
            if (nameAttr != null) {
                location = context.getValueLocation(nameAttr);
            } else {
                location = context.getLocation(element);
            }
            context.report(ISSUE, element, location, message);
        }
    }

    @NonNull
    private static String getErrorMessage(@NonNull String name, @NonNull String prefix) {
        return "Resource named '" + name + "' does not start with the project's resource " +
               "prefix '" + prefix + "'; rename to '" + prefix + name + "'?";
    }
}