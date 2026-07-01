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

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;

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
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.SdkUtils;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Checks for resources referenced from the manifest that vary by configuration.
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
            8,
            Severity.FATAL,
            new Implementation(
                    ManifestResourceDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES)));

    /**
     * Attributes in the manifest that are allowed to vary by configuration
     * (specifically for the application tag).
     */
    private static final List<String> ALLOWED_APPLICATION_ATTRS = Arrays.asList(
            ATTR_ICON,
            ATTR_LABEL,
            ATTR_THEME,
            "description",
            "banner",
            "logo",
            "roundIcon"
    );

    /**
     * Attributes allowed to vary for activity/service/receiver/provider elements
     */
    private static final List<String> ALLOWED_COMPONENT_ATTRS = Arrays.asList(
            ATTR_ICON,
            ATTR_LABEL,
            ATTR_THEME,
            "banner",
            "logo",
            "roundIcon",
            "description"
    );

    /** Constructs a new {@link ManifestResourceDetector} */
    public ManifestResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES
                || folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.LAYOUT
                || folderType == ResourceFolderType.XML
                || folderType == ResourceFolderType.MIPMAP
                || folderType == ResourceFolderType.COLOR
                || folderType == ResourceFolderType.ANIM
                || folderType == ResourceFolderType.ANIMATOR
                || folderType == ResourceFolderType.MENU
                || folderType == ResourceFolderType.RAW;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We handle manifest elements specially below
    }

    @Override
    public void visitManifest(@NonNull XmlContext context, @NonNull Element element) {
        // handled via visitAttribute in manifest
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL_ATTRIBUTES;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!context.getMainProject().getReportIssues()) {
            return;
        }

        // Only check the manifest file
        String fileName = context.file.getName();
        if (!fileName.equals(ANDROID_MANIFEST_XML)) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || !value.startsWith("@")) {
            return;
        }

        // Check if this is a resource reference
        if (!value.startsWith("@") || value.startsWith("@{")) {
            return;
        }

        // Skip tools namespace
        String namespaceURI = attribute.getNamespaceURI();
        if (namespaceURI != null && namespaceURI.equals("http://schemas.android.com/tools")) {
            return;
        }

        // Parse the resource reference: @[package:]type/name or @+id/name
        String reference = value;
        if (reference.startsWith("@+")) {
            return; // id declarations are fine
        }
        if (reference.startsWith("@android:")) {
            return; // framework resources are fine
        }

        // Strip leading @
        String withoutAt = reference.substring(1);

        // Determine resource type and name
        int slashIndex = withoutAt.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String typePart = withoutAt.substring(0, slashIndex);
        String resourceName = withoutAt.substring(slashIndex + 1);

        // Handle package prefix in type (e.g. "com.example:string")
        int colonIndex = typePart.indexOf(':');
        if (colonIndex != -1) {
            String packagePart = typePart.substring(0, colonIndex);
            if (!packagePart.isEmpty() && !packagePart.equals(context.getMainProject().getPackage())) {
                // External package reference, skip
                return;
            }
            typePart = typePart.substring(colonIndex + 1);
        }

        ResourceType resourceType = ResourceType.fromFolderName(typePart);
        if (resourceType == null) {
            resourceType = ResourceType.getEnum(typePart);
        }
        if (resourceType == null) {
            return;
        }

        // Check if this attribute is in the allowed list for its element
        Element parentElement = (Element) attribute.getOwnerElement();
        String tagName = parentElement.getTagName();
        String attrLocalName = attribute.getLocalName();
        if (attrLocalName == null) {
            attrLocalName = attribute.getName();
        }

        // Determine if this attribute is allowed to vary by configuration
        boolean isAllowedToVary = isAllowedToVary(tagName, attrLocalName);
        if (isAllowedToVary) {
            return;
        }

        // Now check whether this resource actually varies by configuration
        // by looking at the resource folders
        if (resourceVariesByConfiguration(context, resourceType, resourceName)) {
            String message = String.format(
                    "Resources referenced from the manifest cannot vary by configuration " +
                    "(except for version qualifiers, e.g. `-v21`). Found `%1$s` from `%2$s`",
                    value, context.file.getName());

            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    /**
     * Returns true if the given attribute in the given element is allowed to reference
     * resources that vary by configuration.
     */
    private static boolean isAllowedToVary(@NonNull String tagName, @NonNull String attrName) {
        switch (tagName) {
            case TAG_APPLICATION:
                return ALLOWED_APPLICATION_ATTRS.contains(attrName);
            case TAG_ACTIVITY:
            case TAG_SERVICE:
            case TAG_RECEIVER:
            case TAG_PROVIDER:
                return ALLOWED_COMPONENT_ATTRS.contains(attrName);
            default:
                return false;
        }
    }

    /**
     * Checks whether the given resource varies by configuration (other than version qualifiers).
     */
    private static boolean resourceVariesByConfiguration(
            @NonNull XmlContext context,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName) {

        // Get the resource directories
        List<File> resourceDirs = context.getMainProject().getResourceFolders();
        if (resourceDirs == null || resourceDirs.isEmpty()) {
            return false;
        }

        // The folder type(s) for this resource type
        List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(resourceType);
        if (folderTypes == null || folderTypes.isEmpty()) {
            return false;
        }

        // Look for resource folders that contain this resource with non-version qualifiers
        boolean foundDefault = false;
        boolean foundConfigSpecific = false;

        for (File resourceDir : resourceDirs) {
            File[] folders = resourceDir.listFiles();
            if (folders == null) {
                continue;
            }

            for (File folder : folders) {
                String folderName = folder.getName();

                // Check if this folder type matches
                int dashIndex = folderName.indexOf('-');
                String baseFolderName = dashIndex != -1 ? folderName.substring(0, dashIndex) : folderName;

                ResourceFolderType folderType = ResourceFolderType.getFolderType(baseFolderName);
                if (folderType == null || !folderTypes.contains(folderType)) {
                    continue;
                }

                // Check if this folder contains the resource
                if (!folderContainsResource(folder, resourceType, resourceName)) {
                    continue;
                }

                // Check the qualifiers
                if (dashIndex == -1) {
                    // No qualifiers - this is the default
                    foundDefault = true;
                } else {
                    String qualifiers = folderName.substring(dashIndex + 1);
                    if (hasNonVersionQualifiers(qualifiers)) {
                        foundConfigSpecific = true;
                    }
                }
            }
        }

        return foundConfigSpecific;
    }

    /**
     * Checks whether the given folder contains the specified resource.
     */
    private static boolean folderContainsResource(
            @NonNull File folder,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName) {

        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }

        // For values folders, we'd need to parse XML files to check for the resource.
        // For file-based resources, we check by filename.
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());

        if (folderType == ResourceFolderType.VALUES) {
            // Check inside XML files for the resource declaration
            for (File file : files) {
                if (file.getName().endsWith(".xml") && valueFileContainsResource(file, resourceType, resourceName)) {
                    return true;
                }
            }
            return false;
        } else {
            // File-based resource: look for a file with the resource name
            for (File file : files) {
                String fileName = file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String nameWithoutExtension = dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;
                if (nameWithoutExtension.equals(resourceName)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Checks whether the given values XML file contains a resource of the given type and name.
     */
    private static boolean valueFileContainsResource(
            @NonNull File file,
            @NonNull ResourceType resourceType,
            @NonNull String resourceName) {
        // Parse the file and look for the resource
        try {
            org.w3c.dom.Document doc = SdkUtils.parseDocument(file, false);
            if (doc == null) {
                return false;
            }
            org.w3c.dom.NodeList children = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) child;
                String tagName = element.getTagName();

                // Check if this element matches the resource type
                if (matchesResourceType(tagName, resourceType)) {
                    String nameAttr = element.getAttribute(ATTR_NAME);
                    if (resourceName.equals(nameAttr)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return false;
    }

    /**
     * Checks whether the given XML tag name corresponds to the given resource type.
     */
    private static boolean matchesResourceType(@NonNull String tagName, @NonNull ResourceType resourceType) {
        switch (resourceType) {
            case STRING:
                return tagName.equals("string");
            case BOOL:
                return tagName.equals("bool");
            case INTEGER:
                return tagName.equals("integer");
            case DIMEN:
                return tagName.equals("dimen");
            case COLOR:
                return tagName.equals("color");
            case ARRAY:
                return tagName.equals("string-array") || tagName.equals("integer-array") || tagName.equals("array");
            case STYLE:
                return tagName.equals("style");
            case ATTR:
                return tagName.equals("attr");
            case DECLARE_STYLEABLE:
                return tagName.equals("declare-styleable");
            case FRACTION:
                return tagName.equals("fraction");
            default:
                return tagName.equals(resourceType.getName());
        }
    }

    /**
     * Returns true if the given qualifier string contains non-version qualifiers.
     * Version qualifiers look like "v21", "v14", etc.
     */
    private static boolean hasNonVersionQualifiers(@NonNull String qualifiers) {
        if (qualifiers.isEmpty()) {
            return false;
        }

        // Split by '-' and check each qualifier
        String[] parts = qualifiers.split("-");
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            // Version qualifiers are like "v14", "v21", etc.
            if (part.matches("v\\d+")) {
                continue;
            }
            // This is a non-version qualifier
            return true;
        }
        return false;
    }

    /**
     * Helper class to map ResourceType to ResourceFolderType(s).
     */
    private static class FolderTypeRelationship {
        @NonNull
        static List<ResourceFolderType> getRelatedFolders(@NonNull ResourceType type) {
            switch (type) {
                case DRAWABLE:
                    return Arrays.asList(ResourceFolderType.DRAWABLE, ResourceFolderType.MIPMAP);
                case MIPMAP:
                    return Collections.singletonList(ResourceFolderType.MIPMAP);
                case LAYOUT:
                    return Collections.singletonList(ResourceFolderType.LAYOUT);
                case ANIM:
                    return Collections.singletonList(ResourceFolderType.ANIM);
                case ANIMATOR:
                    return Collections.singletonList(ResourceFolderType.ANIMATOR);
                case COLOR:
                    return Arrays.asList(ResourceFolderType.COLOR, ResourceFolderType.VALUES);
                case MENU:
                    return Collections.singletonList(ResourceFolderType.MENU);
                case RAW:
                    return Collections.singletonList(ResourceFolderType.RAW);
                case XML:
                    return Collections.singletonList(ResourceFolderType.XML);
                case STRING:
                case BOOL:
                case INTEGER:
                case DIMEN:
                case ARRAY:
                case STYLE:
                case ATTR:
                case DECLARE_STYLEABLE:
                case FRACTION:
                case PLURALS:
                    return Collections.singletonList(ResourceFolderType.VALUES);
                default:
                    return Collections.singletonList(ResourceFolderType.VALUES);
            }
        }
    }
}