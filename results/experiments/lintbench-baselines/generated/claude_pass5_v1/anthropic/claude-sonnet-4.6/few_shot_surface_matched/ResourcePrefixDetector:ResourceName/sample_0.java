package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

public class ResourcePrefixDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ResourceName",
                    "Resource with Wrong Prefix",
                    "In Gradle projects you can specify a resource prefix that all resources "
                            + "in the project must conform to. This makes it easier to ensure that you don't "
                            + "accidentally combine resources from different libraries, since they all end "
                            + "up in the same shared app namespace.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ResourcePrefixDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)));

    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";

    @Nullable
    private String mPrefix;

    public ResourcePrefixDetector() {
    }

    // ---- Implements Detector ----

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        mPrefix = project.getResourcePrefix();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        mPrefix = null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Nothing to do here; prefix is set per-project
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "declare-styleable",
                "eat-comment",
                "item",
                "string",
                "string-array",
                "plurals",
                "integer",
                "integer-array",
                "bool",
                "dimen",
                "color",
                "fraction",
                "style",
                "attr",
                "declare-styleable",
                "drawable",
                "layout",
                "menu",
                "anim",
                "animator",
                "interpolator",
                "transition",
                "xml",
                "raw",
                "font",
                "array",
                "public",
                "java-symbol",
                "overlayable",
                "stagefright-codecs"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        // Get the name attribute of the resource element
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            // Try the android:name attribute
            NamedNodeMap attributes = element.getAttributes();
            if (attributes != null) {
                for (int i = 0; i < attributes.getLength(); i++) {
                    Attr attr = (Attr) attributes.item(i);
                    if (ATTR_NAME.equals(attr.getLocalName())) {
                        name = attr.getValue();
                        break;
                    }
                }
            }
        }

        if (name == null || name.isEmpty()) {
            return;
        }

        // Skip elements that are not resource declarations (e.g., root <resources> tag)
        String tagName = element.getTagName();
        if ("resources".equals(tagName)) {
            return;
        }

        // For <item> elements, check if they have a type attribute (making them a resource)
        if ("item".equals(tagName)) {
            String type = element.getAttribute(ATTR_TYPE);
            if (type == null || type.isEmpty()) {
                return;
            }
        }

        // Check if the resource name starts with the required prefix
        if (!name.startsWith(mPrefix)) {
            String message = String.format(
                    "Resource named '%1$s' does not start with the project's resource prefix '%2$s'; "
                            + "rename to '%3$s%1$s'?",
                    name, mPrefix, mPrefix);

            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            if (nameAttr != null) {
                context.report(ISSUE, element, context.getValueLocation(nameAttr), message);
            } else {
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return false;
        }
        // Apply to all binary resource folder types
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.RAW
                || folderType == ResourceFolderType.FONT
                || folderType == ResourceFolderType.ANIM
                || folderType == ResourceFolderType.ANIMATOR
                || folderType == ResourceFolderType.INTERPOLATOR
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.MENU
                || folderType == ResourceFolderType.COLOR
                || folderType == ResourceFolderType.XML
                || folderType == ResourceFolderType.TRANSITION;
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        if (mPrefix == null || mPrefix.isEmpty()) {
            return;
        }

        String fileName = context.file.getName();
        // Strip extension
        int dotIndex = fileName.lastIndexOf('.');
        String resourceName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;

        if (resourceName.isEmpty()) {
            return;
        }

        if (!resourceName.startsWith(mPrefix)) {
            String message = String.format(
                    "Resource named '%1$s' does not start with the project's resource prefix '%2$s'; "
                            + "rename to '%3$s%1$s'?",
                    resourceName, mPrefix, mPrefix);
            context.report(ISSUE, context.getLocation(context.file), message);
        }
    }
}