/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_ROUND_ICON;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_PROVIDER;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintClient;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks for resource references in the manifest that vary by configuration.
 */
public class ManifestResourceDetector extends ResourceXmlDetector {

    /** The main issue surfaced by this detector */
    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot "
                    + "vary across configurations (except as a special case, by version, and except "
                    + "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            5,
            Severity.FATAL,
            new Implementation(
                    ManifestResourceDetector.class,
                    Scope.MANIFEST_AND_RESOURCE_SCOPE,
                    Scope.MANIFEST_SCOPE));

    /**
     * Attributes that are allowed to vary by configuration in the manifest (for
     * application-level elements).
     */
    private static final Set<String> ALLOWED_ATTRIBUTES = new HashSet<>(Arrays.asList(
            ATTR_ICON,
            ATTR_LABEL,
            ATTR_ROUND_ICON,
            "description",
            "banner"
    ));

    /**
     * Tags for which label/icon attributes are commonly used and allowed.
     */
    private static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList(
            TAG_APPLICATION,
            TAG_ACTIVITY,
            TAG_SERVICE,
            TAG_RECEIVER,
            TAG_PROVIDER,
            "activity-alias",
            "meta-data"
    ));

    /** Constructs a new {@link ManifestResourceDetector} */
    public ManifestResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false; // We only check manifests
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // handled via visitDocument or checkAttribute
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only care about manifest files
        if (!context.getMainProject().isGradleProject() &&
                !context.file.getName().equals("AndroidManifest.xml")) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Parse the resource reference: @[package:]type/name
        // Strip off the @
        String reference = value.substring(1);
        if (reference.startsWith("+")) {
            // @+id/ references are fine
            return;
        }

        // Find the type
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String typeAndPackage = reference.substring(0, slashIndex);
        String resourceName = reference.substring(slashIndex + 1);

        // Strip package prefix if present
        int colonIndex = typeAndPackage.indexOf(':');
        String resourceTypeName;
        String packageName = null;
        if (colonIndex != -1) {
            packageName = typeAndPackage.substring(0, colonIndex);
            resourceTypeName = typeAndPackage.substring(colonIndex + 1);
        } else {
            resourceTypeName = typeAndPackage;
        }

        // If it references android: package resources, skip
        if ("android".equals(packageName)) {
            return;
        }

        // Get the resource type
        ResourceType resourceType = ResourceType.fromClassName(resourceTypeName);
        if (resourceType == null) {
            return;
        }

        // IDs and booleans used for hardcoded values are fine
        if (resourceType == ResourceType.ID) {
            return;
        }

        // Check the element and attribute
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();
        String attributeName = attribute.getLocalName();
        String attributeNs = attribute.getNamespaceURI();

        // For allowed tags with allowed attributes, we permit configuration-varying resources
        boolean isAllowedAttribute = ANDROID_URI.equals(attributeNs)
                && ALLOWED_ATTRIBUTES.contains(attributeName)
                && ALLOWED_TAGS.contains(tagName);

        if (isAllowedAttribute) {
            return;
        }

        // Now check whether the resource varies by configuration
        // (other than by version qualifier)
        LintClient client = context.getClient();

        // Look up the resource folders for this resource type
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders.isEmpty()) {
            return;
        }

        // Check if the resource has configuration-specific variants
        // (non-version qualifiers)
        boolean hasConfigVariants = hasConfigurationVariants(
                client, resourceFolders, resourceType, resourceName);

        if (hasConfigVariants) {
            String message = String.format(
                    "Resources referenced from the manifest cannot vary by configuration "
                            + "(except for version qualifiers, e.g. `%1$s`). "
                            + "Found resource `%2$s` which has different values for "
                            + "different non-version qualifiers.",
                    value, value);

            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    /**
     * Checks whether a resource has variants that differ by non-version configuration qualifiers.
     */
    private static boolean hasConfigurationVariants(
            @NonNull LintClient client,
            @NonNull List<File> resourceFolders,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName) {

        String folderTypeName = getFolderTypeName(resourceType);
        if (folderTypeName == null) {
            return false;
        }

        boolean foundBase = false;
        boolean foundConfigVariant = false;

        for (File resourceFolder : resourceFolders) {
            File[] folders = resourceFolder.listFiles();
            if (folders == null) {
                continue;
            }

            for (File folder : folders) {
                String folderName = folder.getName();

                // Check if this folder is the right type
                int dashIndex = folderName.indexOf('-');
                String baseFolderType = dashIndex == -1
                        ? folderName
                        : folderName.substring(0, dashIndex);

                if (!baseFolderType.equalsIgnoreCase(folderTypeName)) {
                    continue;
                }

                // Check if a file with this resource name exists in this folder
                boolean resourceExists = false;
                if (resourceType == ResourceType.STRING
                        || resourceType == ResourceType.BOOL
                        || resourceType == ResourceType.INTEGER
                        || resourceType == ResourceType.DIMEN
                        || resourceType == ResourceType.COLOR
                        || resourceType == ResourceType.ARRAY
                        || resourceType == ResourceType.PLURALS
                        || resourceType == ResourceType.STYLE
                        || resourceType == ResourceType.ATTR
                        || resourceType == ResourceType.DECLARE_STYLEABLE) {
                    // Values resources - look in values files
                    resourceExists = valuesResourceExists(folder, resourceType, resourceName);
                } else {
                    // File-based resources
                    resourceExists = fileResourceExists(folder, resourceName);
                }

                if (!resourceExists) {
                    continue;
                }

                // Check the qualifiers
                if (dashIndex == -1) {
                    // Base folder (no qualifiers)
                    foundBase = true;
                } else {
                    // Has qualifiers - check if they're only version qualifiers
                    String qualifiers = folderName.substring(dashIndex + 1);
                    if (!isOnlyVersionQualifier(qualifiers)) {
                        foundConfigVariant = true;
                    }
                }
            }
        }

        return foundConfigVariant;
    }

    /**
     * Returns true if the qualifier string contains only version qualifiers (v<N>).
     */
    private static boolean isOnlyVersionQualifier(@NonNull String qualifiers) {
        // Split by dash and check each qualifier
        String[] parts = qualifiers.split("-");
        for (String part : parts) {
            if (!part.matches("v\\d+")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the resource folder type name for a given resource type.
     */
    @Nullable
    private static String getFolderTypeName(@NonNull ResourceType type) {
        ResourceFolderType folderType = ResourceFolderType.getTypeByName(type.getName());
        if (folderType != null) {
            return folderType.getName();
        }
        // Values-based resources
        switch (type) {
            case STRING:
            case BOOL:
            case INTEGER:
            case DIMEN:
            case COLOR:
            case ARRAY:
            case PLURALS:
            case STYLE:
            case ATTR:
            case DECLARE_STYLEABLE:
                return "values";
            default:
                return null;
        }
    }

    /**
     * Checks whether a file-based resource exists in the given folder.
     */
    private static boolean fileResourceExists(@NonNull File folder, @NonNull String resourceName) {
        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            String fileName = file.getName();
            // Strip extension
            int dotIndex = fileName.lastIndexOf('.');
            String baseName = dotIndex == -1 ? fileName : fileName.substring(0, dotIndex);
            if (baseName.equals(resourceName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a values-based resource exists in the given folder.
     * This is a simplified check - we look for XML files and check their content.
     */
    private static boolean valuesResourceExists(
            @NonNull File folder,
            @NonNull ResourceType type,
            @NonNull String resourceName) {
        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.getName().endsWith(".xml")) {
                // We can't easily parse the XML here without a full parser,
                // but we use a simple heuristic: check if the file name suggests
                // it might contain this resource type, or just return true
                // if the folder exists (conservative approach)
                // For a more accurate check, we'd need to parse the XML
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean appliesToResourceRefs() {
        return false;
    }
}