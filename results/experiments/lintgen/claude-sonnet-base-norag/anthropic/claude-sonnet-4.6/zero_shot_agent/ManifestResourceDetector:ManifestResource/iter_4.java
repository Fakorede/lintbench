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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Checks for resource references in the manifest that are not allowed to vary
 * by configuration (other than by API version).
 */
public class ManifestResourceDetector extends Detector implements XmlScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ManifestResource",
            "Manifest Resource References",
            "Elements in the manifest can reference resources, but those resources cannot " +
            "vary across configurations (except as a special case, by version, and except " +
            "for a few specific package attributes such as the application title and icon).",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    ManifestResourceDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES)));

    // Attributes that are allowed to vary by configuration on certain tags
    private static final List<String> ALLOWED_VARYING_ATTRS = Arrays.asList(
            "label",
            "icon",
            "roundIcon",
            "logo",
            "description",
            "theme",
            "banner"
    );

    // Tags where label/icon etc. are allowed to vary
    private static final List<String> ALLOWED_VARYING_TAGS = Arrays.asList(
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "permission",
            "permission-group",
            "permission-tree",
            "instrumentation"
    );

    /** Constructs a new {@link ManifestResourceDetector} */
    public ManifestResourceDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only process manifest files
        if (!context.getProject().isAndroidProject()) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        String tagName = element.getLocalName();
        if (tagName == null) {
            tagName = element.getTagName();
        }

        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();

            // Only check resource references
            if (value == null || !value.startsWith("@")) {
                continue;
            }

            // Skip tools namespace attributes
            String namespace = attr.getNamespaceURI();
            if (namespace != null && namespace.equals("http://schemas.android.com/tools")) {
                continue;
            }

            // Parse the resource URL
            ResourceUrl url = ResourceUrl.parse(value);
            if (url == null || url.isFramework()) {
                continue;
            }

            String attrName = attr.getLocalName();
            if (attrName == null) {
                attrName = attr.getName();
                // Strip namespace prefix if present
                int colon = attrName.indexOf(':');
                if (colon >= 0) {
                    attrName = attrName.substring(colon + 1);
                }
            }

            // Check if this attribute is allowed to vary by configuration
            if (isAllowedToVary(tagName, attrName)) {
                continue;
            }

            // Check if the resource varies by configuration
            checkResourceVariesByConfiguration(context, attr, url);
        }
    }

    /**
     * Returns true if the given attribute on the given tag is allowed to vary
     * by configuration (e.g., label, icon on application/activity).
     */
    private static boolean isAllowedToVary(@NonNull String tagName, @NonNull String attrName) {
        return ALLOWED_VARYING_ATTRS.contains(attrName) && ALLOWED_VARYING_TAGS.contains(tagName);
    }

    /**
     * Checks whether the resource referenced by the given URL varies by
     * configuration (other than by API version).
     */
    private void checkResourceVariesByConfiguration(
            @NonNull XmlContext context,
            @NonNull Attr attr,
            @NonNull ResourceUrl url) {

        // Look at the resource files in the project to see if any are in
        // configuration-specific folders (other than version qualifiers)
        com.android.tools.lint.client.api.LintClient client = context.getClient();
        ResourceRepository resources = client.getResourceRepository(
                context.getProject(), true, false);
        if (resources == null) {
            // Fall back to checking resource files directly
            checkResourceFilesDirectly(context, attr, url);
            return;
        }

        ResourceType type = url.type;
        String name = url.name;

        List<ResourceItem> items;
        try {
            items = resources.getResources(ResourceNamespace.TODO(), type, name);
        } catch (Exception e) {
            return;
        }

        if (items == null || items.isEmpty()) {
            // Try with RES_AUTO namespace
            try {
                items = resources.getResources(ResourceNamespace.RES_AUTO, type, name);
            } catch (Exception e) {
                return;
            }
        }

        if (items == null || items.isEmpty()) {
            return;
        }

        // Check if any of the items are in a configuration-specific folder
        // (other than version qualifiers)
        String configSpecificFolder = null;

        for (ResourceItem item : items) {
            com.android.ide.common.resources.configuration.FolderConfiguration config =
                    item.getConfiguration();
            if (config == null) {
                continue;
            }

            // Get the folder name to check qualifiers
            String folderName = config.getFolderName(ResourceFolderType.VALUES);
            if (folderName == null || folderName.equals("values")) {
                // Default configuration, no qualifiers - this is fine
                continue;
            }

            // It has some qualifier - check if it's only a version qualifier
            if (isOnlyVersionQualifier(folderName)) {
                // Version-only qualifiers are allowed
                continue;
            }

            // Found a configuration-specific resource
            configSpecificFolder = folderName;
            break;
        }

        if (configSpecificFolder != null) {
            reportIssue(context, attr, url, configSpecificFolder);
        }
    }

    /**
     * Fallback: check resource files directly by looking at the project's resource directories.
     */
    private void checkResourceFilesDirectly(
            @NonNull XmlContext context,
            @NonNull Attr attr,
            @NonNull ResourceUrl url) {

        ResourceType type = url.type;
        String name = url.name;

        // Get the resource directories
        List<File> resourceDirs = context.getProject().getResourceFolders();
        if (resourceDirs == null || resourceDirs.isEmpty()) {
            return;
        }

        String configSpecificFolder = null;

        for (File resDir : resourceDirs) {
            File[] folders = resDir.listFiles();
            if (folders == null) {
                continue;
            }

            for (File folder : folders) {
                String folderName = folder.getName();
                ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
                if (folderType == null) {
                    continue;
                }

                // Check if this folder type matches the resource type
                if (!folderTypeMatchesResourceType(folderType, type)) {
                    continue;
                }

                // Check if this is a configuration-specific folder
                if (folderName.contains("-") && !isOnlyVersionQualifier(folderName)) {
                    // Check if the resource exists in this folder
                    if (resourceExistsInFolder(folder, name, type)) {
                        configSpecificFolder = folderName;
                        break;
                    }
                }
            }

            if (configSpecificFolder != null) {
                break;
            }
        }

        if (configSpecificFolder != null) {
            reportIssue(context, attr, url, configSpecificFolder);
        }
    }

    private static boolean folderTypeMatchesResourceType(
            @NonNull ResourceFolderType folderType,
            @NonNull ResourceType resourceType) {
        switch (resourceType) {
            case STRING:
            case BOOL:
            case INTEGER:
            case DIMEN:
            case COLOR:
            case ARRAY:
            case STYLE:
            case STYLEABLE:
            case PLURALS:
            case FRACTION:
            case ATTR:
                return folderType == ResourceFolderType.VALUES;
            case DRAWABLE:
            case MIPMAP:
                return folderType == ResourceFolderType.DRAWABLE
                        || folderType == ResourceFolderType.MIPMAP;
            case LAYOUT:
                return folderType == ResourceFolderType.LAYOUT;
            case MENU:
                return folderType == ResourceFolderType.MENU;
            case XML:
                return folderType == ResourceFolderType.XML;
            case RAW:
                return folderType == ResourceFolderType.RAW;
            case ANIM:
                return folderType == ResourceFolderType.ANIM;
            case ANIMATOR:
                return folderType == ResourceFolderType.ANIMATOR;
            case INTERPOLATOR:
                return folderType == ResourceFolderType.INTERPOLATOR;
            default:
                return false;
        }
    }

    private static boolean resourceExistsInFolder(
            @NonNull File folder,
            @NonNull String name,
            @NonNull ResourceType type) {
        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }

        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
        if (folderType == ResourceFolderType.VALUES) {
            // For values, we'd need to parse XML - just return true if any values file exists
            // This is a simplified check
            return files.length > 0;
        } else {
            // For file-based resources, check if a file with the right name exists
            for (File file : files) {
                String fileName = file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
                if (baseName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void reportIssue(
            @NonNull XmlContext context,
            @NonNull Attr attr,
            @NonNull ResourceUrl url,
            @NonNull String configSpecificFolder) {
        String message = String.format(
                "Resources referenced from the manifest cannot vary by configuration " +
                "(except for version qualifiers, e.g. `-v21`). Found resource `%1$s` " +
                "in a configuration-specific folder `%2$s`",
                attr.getValue(), configSpecificFolder);

        Location location = context.getValueLocation(attr);
        context.report(ISSUE, attr, location, message);
    }

    /**
     * Returns true if the folder name only has version qualifiers (e.g., "values-v21").
     */
    private static boolean isOnlyVersionQualifier(@NonNull String folderName) {
        // Strip the base folder type prefix (e.g., "values")
        int dashIndex = folderName.indexOf('-');
        if (dashIndex == -1) {
            // No qualifiers at all
            return true;
        }

        String qualifiers = folderName.substring(dashIndex + 1);
        // Split by '-' and check each qualifier
        String[] parts = qualifiers.split("-");
        for (String part : parts) {
            if (!part.matches("v\\d+")) {
                return false;
            }
        }
        return true;
    }
}