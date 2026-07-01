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
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.ResourceContext;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_NS_NAME_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.TAG_RESOURCES;

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

    /** Constructs a new {@link ResourcePrefixDetector} */
    public ResourcePrefixDetector() {
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
            // For value files, check each resource element's name attribute
            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }
            NodeList children = root.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    checkValueElement(context, element, prefix);
                }
            }
        } else {
            // For non-value resource files, the filename itself is the resource name
            String fileName = context.file.getName();
            if (fileName.endsWith(DOT_XML)) {
                fileName = fileName.substring(0, fileName.length() - DOT_XML.length());
            }
            // Strip any density/config qualifiers from the folder name - the file name
            // is the resource name
            if (!fileName.startsWith(prefix)) {
                String message = getErrorMessage(fileName, prefix);
                Location location = Location.create(context.file);
                context.report(ISSUE, location, message);
            }
        }
    }

    private void checkValueElement(@NonNull XmlContext context, @NonNull Element element,
            @NonNull String prefix) {
        String tag = element.getTagName();

        // Handle <declare-styleable> and nested items
        if (tag.equals("declare-styleable")) {
            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            if (nameAttr != null) {
                String name = nameAttr.getValue();
                if (!name.startsWith(prefix)) {
                    String message = getErrorMessage(name, prefix);
                    context.report(ISSUE, element, context.getValueLocation(nameAttr), message);
                }
            }
            // Don't check nested <attr> elements inside declare-styleable
            return;
        }

        // Skip <eat-comment>, <skip>, <item> without name, etc.
        if (tag.equals("eat-comment") || tag.equals("skip")) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();

        // Skip android: prefixed names (framework resources referenced in overlays, etc.)
        if (name.startsWith(ANDROID_NS_NAME_PREFIX)) {
            return;
        }

        // For attr elements that are children of declare-styleable, skip
        // (handled above by returning early from declare-styleable)

        if (!name.startsWith(prefix)) {
            String message = getErrorMessage(name, prefix);
            context.report(ISSUE, element, context.getValueLocation(nameAttr), message);
        }
    }

    // ---- Implements ResourceFolderScanner ----

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        // For non-XML resource files (images, etc.), check the file names
        String prefix = getResourcePrefix(context);
        if (prefix == null) {
            return;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File folder = context.file;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String fileName = file.getName();

            // Skip XML files - they are handled by visitDocument
            if (fileName.endsWith(DOT_XML)) {
                continue;
            }

            // Skip hidden files
            if (fileName.startsWith(".")) {
                continue;
            }

            // Get the resource name (strip extension)
            String resourceName = fileName;
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex != -1) {
                resourceName = fileName.substring(0, dotIndex);
            }

            if (!resourceName.startsWith(prefix)) {
                String message = getErrorMessage(resourceName, prefix);
                Location location = Location.create(file);
                context.report(ISSUE, location, message);
            }
        }
    }

    @Nullable
    private static String getResourcePrefix(@NonNull Context context) {
        return context.getProject().getResourcePrefix();
    }

    @NonNull
    private static String getErrorMessage(@NonNull String name, @NonNull String prefix) {
        return String.format("Resource named '`%1$s`' does not start with the project's resource "
                + "prefix '`%2$s`'; rename to '`%3$s`'?", name, prefix, prefix + name);
    }
}