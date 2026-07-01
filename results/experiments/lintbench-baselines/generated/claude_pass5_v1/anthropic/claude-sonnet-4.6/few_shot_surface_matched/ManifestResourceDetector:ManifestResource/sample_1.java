package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintClient;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.SdkUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class ManifestResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, "
                            + "and except for a few specific package attributes such as the "
                            + "application title and icon).",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            ManifestResourceDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES)));

    /** Attributes on the application element that are allowed to vary by configuration */
    private static final List<String> APPLICATION_ALLOWED_ATTRS =
            Arrays.asList(
                    ATTR_LABEL,
                    ATTR_ICON,
                    ATTR_THEME,
                    "description",
                    "banner",
                    "logo",
                    "roundIcon");

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only check manifest files
        if (!context.getProject().getManifestFiles().contains(context.file)) {
            // Check if this is a manifest file by checking the root element
            if (context.getMainProject().getManifestFiles().contains(context.file)) {
                // ok
            } else {
                return;
            }
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Skip tools namespace attributes
        String namespaceURI = attribute.getNamespaceURI();
        if (namespaceURI != null && namespaceURI.equals("http://schemas.android.com/tools")) {
            return;
        }

        // Parse the resource reference: @[package:]type/name
        String reference = value;
        if (reference.startsWith("@+")) {
            reference = "@" + reference.substring(2);
        }

        // Remove the leading @
        String withoutAt = reference.substring(1);

        // Remove optional package prefix
        int slashIndex = withoutAt.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String typeAndPackage = withoutAt.substring(0, slashIndex);
        String resourceName = withoutAt.substring(slashIndex + 1);

        // Remove package qualifier if present
        String resourceType;
        int colonIndex = typeAndPackage.indexOf(':');
        if (colonIndex != -1) {
            String pkg = typeAndPackage.substring(0, colonIndex);
            resourceType = typeAndPackage.substring(colonIndex + 1);
            // If it's referencing android: resources, skip - those are framework resources
            if ("android".equals(pkg)) {
                return;
            }
        } else {
            resourceType = typeAndPackage;
        }

        if (resourceName.isEmpty() || resourceType.isEmpty()) {
            return;
        }

        // Check if the resource type can vary by configuration
        ResourceType type = ResourceType.fromXmlValue(resourceType);
        if (type == null) {
            return;
        }

        // String resources and other value resources can vary by configuration
        // But certain attributes on application element are allowed
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();
        String attrName = attribute.getLocalName();

        if (NODE_APPLICATION.equals(tagName) && APPLICATION_ALLOWED_ATTRS.contains(attrName)) {
            return;
        }

        // Now check if the resource is defined in a configuration-specific folder
        Project project = context.getProject();
        LintClient client = context.getClient();

        // Check the resource directories for configuration-specific definitions
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders.isEmpty()) {
            return;
        }

        boolean foundInConfigFolder = false;
        boolean foundInDefaultFolder = false;

        for (File resDir : resourceFolders) {
            File[] folders = resDir.listFiles();
            if (folders == null) {
                continue;
            }

            for (File folder : folders) {
                String folderName = folder.getName();

                // Check if it's the right resource type folder
                ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
                if (folderType == null) {
                    continue;
                }

                // Check if this folder type matches our resource type
                if (!folderMatchesResourceType(folderType, type)) {
                    continue;
                }

                // Check if this is a configuration-specific folder (has qualifiers)
                boolean isDefaultFolder = isDefaultFolder(folderName, folderType);

                // Look for the resource file in this folder
                if (resourceExistsInFolder(folder, type, resourceName, folderType)) {
                    if (isDefaultFolder) {
                        foundInDefaultFolder = true;
                    } else {
                        foundInConfigFolder = true;
                    }
                }
            }
        }

        if (foundInConfigFolder) {
            String message =
                    String.format(
                            "Resources referenced from the manifest cannot vary by configuration "
                                    + "(except for version qualifiers, e.g. `%1$s`). "
                                    + "Found resource `%2$s` which varies across configurations",
                            value + "-v24",
                            value);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private static boolean isDefaultFolder(@NonNull String folderName, @NonNull ResourceFolderType folderType) {
        // A default folder has no qualifiers - it's just the base folder type name
        String baseType = folderType.getName();
        return folderName.equals(baseType);
    }

    private static boolean folderMatchesResourceType(
            @NonNull ResourceFolderType folderType, @NonNull ResourceType resourceType) {
        // Map resource types to folder types
        switch (resourceType) {
            case STRING:
            case INTEGER:
            case BOOL:
            case DIMEN:
            case COLOR:
            case ARRAY:
            case FRACTION:
            case PLURALS:
            case STYLE:
            case STYLEABLE:
            case ATTR:
                return folderType == ResourceFolderType.VALUES;
            case DRAWABLE:
                return folderType == ResourceFolderType.DRAWABLE
                        || folderType == ResourceFolderType.MIPMAP;
            case LAYOUT:
                return folderType == ResourceFolderType.LAYOUT;
            case ANIM:
                return folderType == ResourceFolderType.ANIM;
            case ANIMATOR:
                return folderType == ResourceFolderType.ANIMATOR;
            case XML:
                return folderType == ResourceFolderType.XML;
            case RAW:
                return folderType == ResourceFolderType.RAW;
            case MENU:
                return folderType == ResourceFolderType.MENU;
            case MIPMAP:
                return folderType == ResourceFolderType.MIPMAP;
            case TRANSITION:
                return folderType == ResourceFolderType.TRANSITION;
            default:
                return false;
        }
    }

    private static boolean resourceExistsInFolder(
            @NonNull File folder,
            @NonNull ResourceType type,
            @NonNull String resourceName,
            @NonNull ResourceFolderType folderType) {
        if (folderType == ResourceFolderType.VALUES) {
            // For values, the resource is defined inside XML files
            File[] files = folder.listFiles();
            if (files == null) {
                return false;
            }
            for (File file : files) {
                if (file.getName().endsWith(DOT_XML)) {
                    // We can't easily parse the file here without context,
                    // so we just return true if there are XML files in a
                    // configuration-specific values folder
                    // A more thorough implementation would parse the XML
                    return true;
                }
            }
            return false;
        } else {
            // For file-based resources, look for a file with the resource name
            File[] files = folder.listFiles();
            if (files == null) {
                return false;
            }
            for (File file : files) {
                String fileName = file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String nameWithoutExt = dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;
                if (nameWithoutExt.equals(resourceName)) {
                    return true;
                }
            }
            return false;
        }
    }
}