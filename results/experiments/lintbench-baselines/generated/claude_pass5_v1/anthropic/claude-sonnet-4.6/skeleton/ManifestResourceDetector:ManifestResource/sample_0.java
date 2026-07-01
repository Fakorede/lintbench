package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTHORITIES;
import static com.android.SdkConstants.ATTR_HOST;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_LOGO;
import static com.android.SdkConstants.ATTR_MIME_TYPE;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PATH;
import static com.android.SdkConstants.ATTR_PATH_PATTERN;
import static com.android.SdkConstants.ATTR_PATH_PREFIX;
import static com.android.SdkConstants.ATTR_PORT;
import static com.android.SdkConstants.ATTR_SCHEME;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_DATA;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.xml.AndroidManifest.NODE_MANIFEST;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
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
     * Attributes in the manifest that allow resource references but must not vary by configuration.
     * These are attributes where only version qualifiers are allowed.
     */
    private static final Set<String> ALLOWED_ATTRIBUTES =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_LABEL,
                            ATTR_ICON,
                            ATTR_LOGO,
                            ATTR_THEME,
                            ATTR_NAME,
                            ATTR_AUTHORITIES,
                            ATTR_HOST,
                            ATTR_MIME_TYPE,
                            ATTR_PATH,
                            ATTR_PATH_PATTERN,
                            ATTR_PATH_PREFIX,
                            ATTR_PORT,
                            ATTR_SCHEME,
                            "description",
                            "banner",
                            "roundIcon",
                            "networkSecurityConfig",
                            "appComponentFactory",
                            "backupAgent",
                            "manageSpaceActivity",
                            "parentActivityName",
                            "permission",
                            "process",
                            "taskAffinity",
                            "allowTaskReparenting",
                            "exported",
                            "grantUriPermissions",
                            "readPermission",
                            "writePermission",
                            "syncable",
                            "targetActivity",
                            "windowSoftInputMode",
                            "launchMode",
                            "screenOrientation",
                            "configChanges",
                            "hardwareAccelerated",
                            "multiprocess",
                            "enabled",
                            "excludeFromRecents",
                            "noHistory",
                            "alwaysRetainTaskState",
                            "clearTaskOnLaunch",
                            "finishOnTaskLaunch",
                            "stateNotNeeded",
                            "uiOptions",
                            "documentLaunchMode",
                            "maxRecents",
                            "autoRemoveFromRecents",
                            "relinquishTaskIdentity",
                            "resumeWhilePausing",
                            "persistableMode",
                            "rotationAnimation",
                            "lockTaskMode",
                            "showForAllUsers",
                            "resizeableActivity",
                            "supportsPictureInPicture",
                            "maxAspectRatio",
                            "directBootAware",
                            "colorMode",
                            "visibleToInstantApps",
                            "splitName",
                            "usesCleartextTraffic",
                            "extractNativeLibs",
                            "debuggable",
                            "testOnly",
                            "allowBackup",
                            "killAfterRestore",
                            "restoreNeedsApplication",
                            "restoreAnyVersion",
                            "fullBackupOnly",
                            "backupInForeground",
                            "fullBackupContent",
                            "supportsRtl",
                            "largeHeap",
                            "vmSafeMode",
                            "hasCode",
                            "persistent",
                            "requiredAccountType",
                            "restrictedAccountType",
                            "isGame",
                            "cantSaveState",
                            "defaultToDeviceProtectedStorage",
                            "sharedUserId",
                            "sharedUserLabel",
                            "versionCode",
                            "versionName",
                            "installLocation",
                            "isolatedProcess",
                            "singleUser",
                            "priority"
                    ));

    /**
     * Elements in the manifest where we check attributes for resource references
     * that might vary by configuration.
     */
    private static final Set<String> MANIFEST_ELEMENTS =
            new HashSet<>(
                    Arrays.asList(
                            TAG_APPLICATION,
                            TAG_ACTIVITY,
                            TAG_SERVICE,
                            TAG_RECEIVER,
                            TAG_PROVIDER,
                            TAG_INTENT_FILTER,
                            TAG_ACTION,
                            TAG_CATEGORY,
                            TAG_DATA,
                            NODE_MANIFEST,
                            "activity-alias",
                            "meta-data",
                            "uses-library",
                            "uses-feature",
                            "uses-permission",
                            "permission",
                            "permission-group",
                            "permission-tree",
                            "instrumentation",
                            "supports-screens",
                            "compatible-screens",
                            "supports-gl-texture",
                            "uses-configuration",
                            "uses-sdk",
                            "grant-uri-permission",
                            "path-permission"
                    ));

    /**
     * Attributes that are allowed to reference resources that CAN vary by configuration
     * (like @string, @drawable) - these are the "allowed" ones for application-level labels, icons etc.
     */
    private static final Set<String> CONFIGURATION_VARYING_ALLOWED =
            new HashSet<>(
                    Arrays.asList(
                            ATTR_LABEL,
                            ATTR_ICON,
                            ATTR_LOGO,
                            ATTR_THEME,
                            "description",
                            "banner",
                            "roundIcon"
                    ));

    /**
     * Resource types that can vary by configuration and thus should be flagged
     * when referenced from manifest attributes that don't allow it.
     */
    private static final Set<String> CONFIGURATION_VARYING_TYPES =
            new HashSet<>(
                    Arrays.asList(
                            "string",
                            "drawable",
                            "color",
                            "dimen",
                            "integer",
                            "bool",
                            "layout",
                            "anim",
                            "animator",
                            "interpolator",
                            "menu",
                            "raw",
                            "xml",
                            "font",
                            "array",
                            "plurals",
                            "style",
                            "styleable",
                            "transition",
                            "navigation"
                    ));

    /**
     * Resource types that cannot vary by configuration and are always safe.
     */
    private static final Set<String> NON_VARYING_TYPES =
            new HashSet<>(
                    Arrays.asList(
                            "mipmap"  // mipmap is typically used for launcher icons and is OK
                    ));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        // This detector applies to all resource files, but we only care about
        // manifest-like resource files. Actually, this is a ResourceXmlDetector
        // so it will be called for resource files. We primarily want to check
        // resource files that are referenced from the manifest.
        // The actual manifest checking is done via a separate manifest scope.
        // For the resource XML detector, we check if resources are defined in
        // a way that varies by configuration when referenced from manifest.
        return true;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        // We want to check all attributes - we'll filter in visitAttribute
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only process manifest files
        if (!context.getFile().getName().equals("AndroidManifest.xml")) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith(PREFIX_RESOURCE_REF)) {
            return;
        }

        // Parse the resource URL
        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null) {
            return;
        }

        // Skip framework resources
        if (url.isFramework()) {
            return;
        }

        String resourceType = url.type.getName();

        // Check if this is a resource type that can vary by configuration
        if (!CONFIGURATION_VARYING_TYPES.contains(resourceType)) {
            return;
        }

        // Get the element containing this attribute
        Element element = attribute.getOwnerElement();
        String elementName = element.getLocalName();
        if (elementName == null) {
            elementName = element.getTagName();
        }

        String attributeName = attribute.getLocalName();
        if (attributeName == null) {
            attributeName = attribute.getName();
        }

        // Check if this attribute is allowed to vary by configuration
        // For label, icon, logo, theme, description, banner, roundIcon on application/activity,
        // it's generally OK because the system handles these specially
        if (CONFIGURATION_VARYING_ALLOWED.contains(attributeName)) {
            // These attributes are allowed to reference configuration-varying resources
            // for certain elements (application, activity, etc.)
            if (isAllowedElement(elementName)) {
                return;
            }
        }

        // For other attributes in the manifest that reference configuration-varying resources,
        // we need to check if the resource has configuration-specific variants
        // that would be problematic.
        // 
        // The key issue is: if a manifest attribute references a resource that has
        // different values for different configurations (like different string values
        // for different locales), the manifest won't be able to use the correct value
        // at install time.

        // Check if the resource file itself is in a configuration-specific folder
        // (e.g., values-en, values-land, etc.)
        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType == null) {
            // We're in a manifest file, not a resource file
            // Check if the referenced resource might vary by configuration
            // by looking at whether the resource exists in configuration folders
            checkManifestResourceReference(context, attribute, url, attributeName, elementName);
        }
    }

    private boolean isAllowedElement(String elementName) {
        return TAG_APPLICATION.equals(elementName)
                || TAG_ACTIVITY.equals(elementName)
                || TAG_SERVICE.equals(elementName)
                || TAG_RECEIVER.equals(elementName)
                || TAG_PROVIDER.equals(elementName)
                || "activity-alias".equals(elementName)
                || "permission".equals(elementName)
                || "permission-group".equals(elementName)
                || "instrumentation".equals(elementName);
    }

    private void checkManifestResourceReference(
            @NonNull XmlContext context,
            @NonNull Attr attribute,
            @NonNull ResourceUrl url,
            @NonNull String attributeName,
            @NonNull String elementName) {

        // For attributes that are not in the "allowed to vary" set,
        // we flag any reference to a potentially configuration-varying resource type
        // if we can determine it actually has configuration variants.

        // The safest approach is to check if the resource has variants in the
        // resource repository. Since we're in a lint check, we can use the
        // context's client to look up the resource.

        // For simplicity in this implementation, we check:
        // 1. If the attribute is one that definitely shouldn't have config-varying resources
        // 2. If the resource type is one that commonly varies by configuration

        String resourceType = url.type.getName();

        // String resources almost always vary by locale
        // Layout resources vary by orientation, screen size, etc.
        // These should not be used in most manifest attributes

        boolean isProblematic = false;
        String message = null;

        if (CONFIGURATION_VARYING_ALLOWED.contains(attributeName)) {
            // Already handled above - these are OK for allowed elements
            return;
        }

        // Check for string resources in attributes that shouldn't vary
        if ("string".equals(resourceType)) {
            // String resources vary by locale - problematic in most manifest attributes
            // except label, description, etc. which we already allowed above
            isProblematic = true;
            message =
                    "Resource `"
                            + url
                            + "` referenced from `"
                            + elementName
                            + "` cannot vary by configuration (such as locale); "
                            + "translate the string in `strings.xml` but use a constant value in the manifest";
        } else if ("color".equals(resourceType) || "dimen".equals(resourceType)
                || "bool".equals(resourceType) || "integer".equals(resourceType)) {
            // These can vary by configuration (e.g., different values for different screen sizes)
            isProblematic = true;
            message =
                    "Resource `"
                            + url
                            + "` referenced from the manifest cannot vary by configuration";
        } else if ("drawable".equals(resourceType) || "layout".equals(resourceType)) {
            // Drawables and layouts can vary by many configurations
            if (!CONFIGURATION_VARYING_ALLOWED.contains(attributeName)) {
                isProblematic = true;
                message =
                        "Resource `"
                                + url
                                + "` referenced from the manifest cannot vary by configuration";
            }
        }

        if (isProblematic && message != null) {
            // Check if the resource actually has configuration-specific variants
            // by examining what's available in the project
            if (hasConfigurationVariants(context, url)) {
                context.report(ISSUE, attribute, context.getLocation(attribute), message);
            }
        }
    }

    /**
     * Check if a resource has configuration-specific variants that would
     * make it problematic to use from the manifest.
     */
    private boolean hasConfigurationVariants(
            @NonNull XmlContext context, @NonNull ResourceUrl url) {
        // Use the lint client to check if the resource has configuration variants
        // In a real implementation, we'd query the resource repository
        // For this implementation, we'll use a conservative approach:
        // Flag it if we can determine there are configuration-specific variants

        com.android.tools.lint.detector.api.LintClient client = context.getClient();
        com.android.ide.common.resources.ResourceRepository resources =
                client.getResourceRepository(context.getProject(), true, false);

        if (resources == null) {
            // Can't determine - be conservative and don't flag
            return false;
        }

        java.util.List<com.android.ide.common.res2.ResourceItem> items =
                resources.getResourceItem(url.type, url.name);

        if (items == null || items.isEmpty()) {
            return false;
        }

        // Check if any items have non-default configuration qualifiers
        // that would indicate configuration-varying behavior
        for (com.android.ide.common.res2.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config =
                    item.getConfiguration();
            if (config != null && !config.isDefault()) {
                // Has configuration-specific variants
                // But check if the only qualifier is version (which is OK)
                if (hasNonVersionQualifiers(config)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if a folder configuration has qualifiers other than version qualifiers.
     * Version qualifiers (like v21, v23) are allowed in manifest resources.
     */
    private boolean hasNonVersionQualifiers(
            @NonNull com.android.ide.common.resources.configuration.FolderConfiguration config) {
        // Check all qualifiers except version qualifier
        // If there are locale, density, orientation, etc. qualifiers, return true

        // Version qualifier is the only one allowed for manifest resources
        com.android.ide.common.resources.configuration.VersionQualifier versionQualifier =
                config.getVersionQualifier();

        // Create a copy and remove the version qualifier to see if anything remains
        com.android.ide.common.resources.configuration.FolderConfiguration copy =
                com.android.ide.common.resources.configuration.FolderConfiguration.copyOf(config);
        copy.setVersionQualifier(null);

        // If the config without version qualifier is not the default, it has other qualifiers
        return !copy.isDefault();
    }
}