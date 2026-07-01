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
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.SdkUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for resource references in the manifest that cannot vary by configuration.
 */
public class ManifestResourceDetector extends ResourceXmlDetector {

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

    /**
     * Set of attributes in the manifest that ARE allowed to vary by configuration.
     * These are typically the label, icon, roundIcon on the application element.
     */
    private static final List<String> ALLOWED_ATTRIBUTES = Arrays.asList(
            ATTR_LABEL,
            ATTR_ICON,
            ATTR_ROUND_ICON,
            "description",
            "banner"
    );

    /**
     * Set of tags where label/icon etc. are allowed to vary.
     */
    private static final List<String> ALLOWED_TAGS = Arrays.asList(
            TAG_APPLICATION,
            TAG_ACTIVITY,
            TAG_SERVICE,
            TAG_RECEIVER,
            TAG_PROVIDER,
            "activity-alias"
    );

    /**
     * Map from resource name to the list of locations where it's referenced from the manifest.
     * We collect these and then check after all resources are visited whether any of the
     * resources vary by configuration (other than version).
     */
    private Map<String, List<Location.Handle>> mManifestResourceReferences;

    /**
     * Map from resource key (type/name) to whether it varies by non-version configuration.
     */
    private Map<String, Boolean> mResourceVariesMap;

    /** Constructs a new {@link ManifestResourceDetector} */
    public ManifestResourceDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getProject().isGradleProject()) {
            // In Gradle projects we handle this differently
        }

        if (!context.file.getName().equals("AndroidManifest.xml")) {
            // This is a resource file - check if it varies by non-version configuration
            checkResourceFile(context);
            return;
        }

        // This is the manifest - collect resource references
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        String tag = element.getTagName();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String value = attr.getValue();
            if (value.startsWith("@")) {
                // This is a resource reference
                checkManifestResourceReference(context, element, attr, value);
            }
        }
    }

    private void checkManifestResourceReference(
            @NonNull XmlContext context,
            @NonNull Element element,
            @NonNull Attr attr,
            @NonNull String value) {

        // Skip tools: namespace attributes
        String namespace = attr.getNamespaceURI();
        if (namespace != null && namespace.startsWith("http://schemas.android.com/tools")) {
            return;
        }

        // Parse the resource reference: @[+][package:]type/name
        String reference = value;
        if (reference.startsWith("@+")) {
            reference = reference.substring(2);
        } else if (reference.startsWith("@")) {
            reference = reference.substring(1);
        }

        // Skip references to android: package
        if (reference.startsWith("android:")) {
            return;
        }

        // Remove package prefix if present (for local package)
        int colonIndex = reference.indexOf(':');
        if (colonIndex >= 0) {
            // Has a package - skip if it's not the local package
            String packageName = reference.substring(0, colonIndex);
            if (!packageName.isEmpty() && !packageName.equals("android")) {
                // Could be local package reference - strip it
                reference = reference.substring(colonIndex + 1);
            } else if (packageName.equals("android")) {
                return;
            }
        }

        // Now reference should be "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex < 0) {
            return;
        }

        String typeName = reference.substring(0, slashIndex);
        String resourceName = reference.substring(slashIndex + 1);

        ResourceType type = ResourceType.fromXmlValue(typeName);
        if (type == null) {
            return;
        }

        // Check if this attribute is allowed to vary
        String attrLocalName = attr.getLocalName();
        if (attrLocalName == null) {
            attrLocalName = attr.getName();
        }
        // Remove namespace prefix if present
        int prefixColon = attrLocalName.indexOf(':');
        if (prefixColon >= 0) {
            attrLocalName = attrLocalName.substring(prefixColon + 1);
        }

        String tag = element.getTagName();
        boolean isAllowedToVary = ALLOWED_TAGS.contains(tag) &&
                ALLOWED_ATTRIBUTES.contains(attrLocalName);

        if (isAllowedToVary) {
            // These are allowed to vary by configuration
            return;
        }

        // For string resources referenced from allowed attributes but on non-allowed tags,
        // or for other resource types - check if they vary by configuration
        String key = typeName + "/" + resourceName;

        if (mManifestResourceReferences == null) {
            mManifestResourceReferences = new HashMap<>();
        }

        List<Location.Handle> handles = mManifestResourceReferences.get(key);
        if (handles == null) {
            handles = new ArrayList<>();
            mManifestResourceReferences.put(key, handles);
        }
        handles.add(context.createLocationHandle(attr));
    }

    private void checkResourceFile(@NonNull XmlContext context) {
        File file = context.file;
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }
        String folderName = folder.getName();

        // Parse the folder configuration
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(folderName);
        if (config == null) {
            return;
        }

        // Check if this configuration varies by something other than version
        // A resource varies "by configuration" if it has qualifiers beyond just version
        if (variesByNonVersionConfig(config)) {
            // Record that resources in this file may vary
            if (mResourceVariesMap == null) {
                mResourceVariesMap = new HashMap<>();
            }

            ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
            if (folderType == null) {
                return;
            }

            // We need to know which resources are defined in this file
            // We'll mark the folder type + all resource names from this file
            // Actually, we'll store by resource key
            recordVaryingResources(context, folderType);
        }
    }

    private void recordVaryingResources(@NonNull XmlContext context,
            @NonNull ResourceFolderType folderType) {
        // Get the resource type from folder type
        List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        if (types.isEmpty()) {
            return;
        }

        // For values folders, we need to look at the actual elements
        if (folderType == ResourceFolderType.VALUES) {
            // The element visitor will handle individual value resources
            // We store context for later use
        }

        // For non-values resources (like layout, drawable, etc.), the resource name
        // is the filename without extension
        if (folderType != ResourceFolderType.VALUES) {
            String fileName = context.file.getName();
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex >= 0) {
                fileName = fileName.substring(0, dotIndex);
            }

            ResourceType primaryType = types.get(0);
            String key = primaryType.getName() + "/" + fileName;
            if (mResourceVariesMap == null) {
                mResourceVariesMap = new HashMap<>();
            }
            mResourceVariesMap.put(key, Boolean.TRUE);
        }
    }

    /**
     * Returns true if the given folder configuration varies by something other than
     * just the API version (v<N> qualifier).
     */
    private static boolean variesByNonVersionConfig(@NonNull FolderConfiguration config) {
        // A default configuration (no qualifiers) doesn't vary
        if (config.isDefault()) {
            return false;
        }

        // Check if there are any qualifiers other than version
        // We do this by creating a copy, clearing the version qualifier, and checking
        // if anything remains
        FolderConfiguration copy = FolderConfiguration.copyOf(config);
        copy.setVersionQualifier(null);

        return !copy.isDefault();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mManifestResourceReferences == null || mManifestResourceReferences.isEmpty()) {
            return;
        }

        if (mResourceVariesMap == null) {
            mResourceVariesMap = new HashMap<>();
        }

        // Now check each manifest resource reference to see if the resource varies
        for (Map.Entry<String, List<Location.Handle>> entry :
                mManifestResourceReferences.entrySet()) {
            String key = entry.getKey();
            List<Location.Handle> handles = entry.getValue();

            Boolean varies = mResourceVariesMap.get(key);
            if (varies != null && varies) {
                // This resource varies by configuration - report each usage
                for (Location.Handle handle : handles) {
                    Location location = handle.resolve();
                    context.report(
                            ISSUE,
                            location,
                            "Resources referenced from the manifest cannot vary by " +
                            "configuration (except for version qualifiers, e.g. `-v21`). " +
                            "Found variation in " + key);
                }
            }
        }
    }

    // ---- Helper class for folder type to resource type relationship ----

    /**
     * Helper to get resource types from folder types without importing internal classes.
     */
    private static class FolderTypeRelationship {
        static List<ResourceType> getRelatedResourceTypes(ResourceFolderType folderType) {
            List<ResourceType> types = new ArrayList<>();
            switch (folderType) {
                case ANIM:
                    types.add(ResourceType.ANIM);
                    break;
                case ANIMATOR:
                    types.add(ResourceType.ANIMATOR);
                    break;
                case COLOR:
                    types.add(ResourceType.COLOR);
                    break;
                case DRAWABLE:
                    types.add(ResourceType.DRAWABLE);
                    break;
                case FONT:
                    types.add(ResourceType.FONT);
                    break;
                case INTERPOLATOR:
                    types.add(ResourceType.INTERPOLATOR);
                    break;
                case LAYOUT:
                    types.add(ResourceType.LAYOUT);
                    break;
                case MENU:
                    types.add(ResourceType.MENU);
                    break;
                case MIPMAP:
                    types.add(ResourceType.MIPMAP);
                    break;
                case RAW:
                    types.add(ResourceType.RAW);
                    break;
                case TRANSITION:
                    types.add(ResourceType.TRANSITION);
                    break;
                case VALUES:
                    types.add(ResourceType.STRING);
                    types.add(ResourceType.BOOL);
                    types.add(ResourceType.INTEGER);
                    types.add(ResourceType.DIMEN);
                    types.add(ResourceType.COLOR);
                    types.add(ResourceType.ARRAY);
                    types.add(ResourceType.STYLE);
                    types.add(ResourceType.ATTR);
                    break;
                case XML:
                    types.add(ResourceType.XML);
                    break;
                default:
                    break;
            }
            return types;
        }
    }
}