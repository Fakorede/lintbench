package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTHORITIES;
import static com.android.SdkConstants.ATTR_HOST;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_MIME_TYPE;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PATH;
import static com.android.SdkConstants.ATTR_PATH_PATTERN;
import static com.android.SdkConstants.ATTR_PATH_PREFIX;
import static com.android.SdkConstants.ATTR_PORT;
import static com.android.SdkConstants.ATTR_SCHEME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_LOGO;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ManifestResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestResource",
                    "Manifest Resource References",
                    "Elements in the manifest can reference resources, but those resources cannot "
                            + "vary across configurations (except as a special case, by version, and except "
                            + "for a few specific package attributes such as the application title and icon).",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /**
     * Attributes in the manifest that are allowed to reference resources which may vary by
     * configuration (e.g., strings, drawables for label and icon on application/activity/etc.)
     */
    private static final Set<String> ALLOWED_VARYING_ATTRS =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_LABEL,
                            ATTR_ICON,
                            ATTR_BANNER,
                            ATTR_LOGO,
                            "description",
                            "roundIcon"));

    /**
     * Tags for components where label/icon are allowed to vary.
     */
    private static final Set<String> COMPONENT_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            TAG_APPLICATION,
                            TAG_ACTIVITY,
                            TAG_ACTIVITY_ALIAS,
                            TAG_SERVICE,
                            TAG_RECEIVER,
                            TAG_PROVIDER));

    /**
     * Attributes in the manifest that must not reference configuration-varying resources.
     * These are attributes that appear in the manifest and reference resources.
     */
    private static final Set<String> CHECKED_ATTRS =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_NAME,
                            ATTR_AUTHORITIES,
                            ATTR_HOST,
                            ATTR_MIME_TYPE,
                            ATTR_PATH,
                            ATTR_PATH_PATTERN,
                            ATTR_PATH_PREFIX,
                            ATTR_PORT,
                            ATTR_SCHEME,
                            // permission-related
                            "permission",
                            "process",
                            "taskAffinity",
                            "targetActivity",
                            "targetPackage",
                            "readPermission",
                            "writePermission",
                            "parentActivityName",
                            "permission",
                            "protectionLevel",
                            "permissionGroup",
                            // manifest attributes that reference resources but shouldn't vary
                            "value",
                            "resource",
                            "mimeType",
                            "data",
                            "action",
                            "category",
                            "type",
                            "package",
                            "sharedUserId",
                            "versionName",
                            "installLocation",
                            "backupAgent",
                            "manageSpaceActivity",
                            "networkSecurityConfig",
                            "appComponentFactory",
                            "zygotePreloadName"));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        // This detector applies to all resource files; the actual filtering
        // for manifest is done by checking the file name in visitAttribute.
        return true;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about files inside the manifest or resource files that
        // might be referenced from the manifest. Actually, this detector
        // should check resource files that have configuration qualifiers
        // to warn when those resources are referenced from the manifest.
        //
        // The main check: if we're looking at a resource file that has
        // configuration qualifiers (other than version), we should warn
        // if those resources might be referenced from the manifest in
        // non-allowed ways.
        //
        // However, the standard approach for ManifestResourceDetector is:
        // Check resource XML files (not the manifest itself), and if
        // the resource folder has configuration qualifiers that would
        // cause the resource to vary (other than version), report it.

        String fileName = context.file.getName();

        // We only care about resource files, not the manifest itself
        if (ANDROID_MANIFEST_XML.equals(fileName)) {
            return;
        }

        // Check if this resource file is in a folder with configuration qualifiers
        // that would cause it to vary (other than version qualifiers).
        String folderName = context.file.getParentFile().getName();
        if (!hasNonVersionQualifiers(folderName)) {
            return;
        }

        // Get the attribute value
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        // We only care about attributes that reference resources (start with @)
        // Actually for this detector, we want to check if the resource defined
        // in this file (with configuration qualifiers) could be problematic
        // when referenced from the manifest.
        //
        // The typical approach: check if the resource type of this file
        // is one that can be referenced from manifest in restricted ways.

        // Get the resource folder type
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            return;
        }

        // Check the element and attribute to see if this is a manifest-sensitive reference
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();
        String attrName = attribute.getLocalName();
        String attrNamespaceURI = attribute.getNamespaceURI();

        // For resource files with non-version qualifiers, check if the attribute
        // value is being used in a context that would be referenced from the manifest.
        // The key insight: if a resource file has configuration qualifiers other than
        // version, it shouldn't be referenced from the manifest (with exceptions).

        // Check if this attribute is in the android namespace
        boolean isAndroidAttr = ANDROID_URI.equals(attrNamespaceURI);

        // Determine if this attribute/element combination is allowed to vary
        if (isAllowedToVary(tagName, attrName, isAndroidAttr)) {
            return;
        }

        // Check if the value references a resource
        if (!value.startsWith("@") && !value.startsWith("?")) {
            return;
        }

        // Report the issue
        context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                String.format(
                        "Resources referenced from the manifest cannot vary by configuration "
                                + "(except for version qualifiers, e.g. `-v21`). Found resource `%1$s` "
                                + "in configuration-specific folder `%2$s`",
                        value,
                        folderName));
    }

    /**
     * Returns true if the folder name has configuration qualifiers other than version qualifiers.
     */
    private static boolean hasNonVersionQualifiers(@NonNull String folderName) {
        // Folder names are like "values", "values-en", "values-v21", "layout-land", etc.
        int dashIndex = folderName.indexOf('-');
        if (dashIndex == -1) {
            // No qualifiers at all
            return false;
        }

        String qualifiers = folderName.substring(dashIndex + 1);
        String[] parts = qualifiers.split("-");

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            // Version qualifiers look like "v21", "v14", etc.
            if (part.matches("v\\d+")) {
                continue;
            }
            // If we find any non-version qualifier, return true
            return true;
        }

        return false;
    }

    /**
     * Returns true if the given attribute on the given element is allowed to reference
     * configuration-varying resources (e.g., label and icon on application/activity).
     */
    private static boolean isAllowedToVary(
            @NonNull String tagName, @NonNull String attrName, boolean isAndroidAttr) {
        if (!isAndroidAttr) {
            return true;
        }

        if (ALLOWED_VARYING_ATTRS.contains(attrName)) {
            if (COMPONENT_TAGS.contains(tagName)) {
                return true;
            }
        }

        return false;
    }
}