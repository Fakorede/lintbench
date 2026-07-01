package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_AUTHORITIES;
import static com.android.SdkConstants.ATTR_DESCRIPTION;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_LOGO;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.ATTR_PROCESS;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.ATTR_RESOURCE;
import static com.android.SdkConstants.ATTR_SCHEME;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_APPLICATION;

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
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.MANIFEST)));

    /** Attributes in the manifest that are allowed to vary by configuration */
    private static final Collection<String> ALLOWED_VARYING_ATTRS =
            Arrays.asList(
                    ATTR_LABEL,
                    ATTR_ICON,
                    ATTR_LOGO,
                    ATTR_DESCRIPTION,
                    ATTR_THEME);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only check manifest files
        if (!context.getProject().getManifestFiles().contains(context.file)) {
            return;
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

        // Allow certain attributes that are explicitly permitted to vary
        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
        }

        // These attributes are allowed to reference resources that vary by configuration
        if (ALLOWED_VARYING_ATTRS.contains(localName)) {
            return;
        }

        // Check if the resource reference points to a configuration-specific resource
        // Parse the resource reference
        String resourceRef = value.substring(1); // strip leading '@'
        if (resourceRef.startsWith("@")) {
            // @@string/... style - not a resource reference
            return;
        }

        // Strip optional package prefix (e.g. "android:string/foo" or "string/foo")
        int slashIndex = resourceRef.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String typeAndName = resourceRef;
        int colonIndex = resourceRef.indexOf(':');
        if (colonIndex != -1 && colonIndex < slashIndex) {
            // Has package prefix, e.g. "android:string/foo"
            // We only care about resources in the current project, skip android: ones
            String pkg = resourceRef.substring(0, colonIndex);
            if (!pkg.isEmpty() && !pkg.equals(context.getProject().getPackage())) {
                return;
            }
            typeAndName = resourceRef.substring(colonIndex + 1);
            slashIndex = typeAndName.indexOf('/');
            if (slashIndex == -1) {
                return;
            }
        }

        String resourceType = typeAndName.substring(0, slashIndex);
        String resourceName = typeAndName.substring(slashIndex + 1);

        if (resourceType.isEmpty() || resourceName.isEmpty()) {
            return;
        }

        // Check if the resource has configuration-specific variants
        // by looking at the resource repository
        if (context.getClient() != null) {
            try {
                com.android.tools.lint.detector.api.LintClient client = context.getClient();
                com.android.ide.common.resources.ResourceRepository resources =
                        client.getResourceRepository(context.getProject(), true, false);
                if (resources != null) {
                    com.android.resources.ResourceType type =
                            com.android.resources.ResourceType.fromXmlValue(resourceType);
                    if (type == null) {
                        return;
                    }
                    java.util.List<com.android.ide.common.resources.ResourceItem> items =
                            resources.getResourceItem(type, resourceName);
                    if (items != null && hasConfigurationSpecificItems(items)) {
                        context.report(
                                ISSUE,
                                attribute,
                                context.getLocation(attribute),
                                String.format(
                                        "Resources referenced from the manifest cannot vary by "
                                                + "configuration (except for version qualifiers, "
                                                + "e.g. `%1$s`). Found `%2$s` from `%3$s`",
                                        value,
                                        resourceName,
                                        attribute.getName()));
                    }
                }
            } catch (Exception ignore) {
                // If resource lookup fails, skip
            }
        }
    }

    private static boolean hasConfigurationSpecificItems(
            java.util.List<com.android.ide.common.resources.ResourceItem> items) {
        if (items.size() <= 1) {
            return false;
        }

        for (com.android.ide.common.resources.ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config =
                    item.getConfiguration();
            if (config != null) {
                // Check if the only varying qualifier is the version qualifier
                // If there are other qualifiers, report as an issue
                com.android.ide.common.resources.configuration.FolderConfiguration
                        configWithoutVersion = config.clone();
                configWithoutVersion.setVersionQualifier(null);
                if (!configWithoutVersion.isDefault()) {
                    return true;
                }
            }
        }

        return false;
    }
}