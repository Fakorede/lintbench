/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for duplicate resource definitions.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definition",
            "You can define a resource multiple times in different resource folders; that's how "
                    + "string translations are done, for example. However, defining the same resource "
                    + "more than once in the same resource folder is likely an error, for example "
                    + "attempting to add a new resource without realizing that the name is already used, "
                    + "and so on.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from resource folder (the parent folder, e.g. "values" or "values-de") to
     * a map from resource key (type+name) to the location where it was first defined.
     */
    private Map<File, Map<String, Location>> mFolderToResourceMap;

    /** Constructs a new {@link DuplicateResourceDetector} */
    public DuplicateResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "string",
                "string-array",
                "plurals",
                "integer",
                "integer-array",
                "bool",
                "dimen",
                "color",
                "style",
                "declare-styleable",
                "attr",
                "drawable",
                "layout",
                "menu",
                "fraction",
                TAG_ITEM
        );
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (mFolderToResourceMap == null) {
            mFolderToResourceMap = new HashMap<>();
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Get the resource name
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // Determine the resource type
        String tagName = element.getTagName();
        String type;
        if (TAG_ITEM.equals(tagName)) {
            type = element.getAttribute(ATTR_TYPE);
            if (type == null || type.isEmpty()) {
                // Can't determine type for this item
                return;
            }
        } else {
            type = tagName;
        }

        // Build the resource key
        String key = type + "/" + name;

        // Get the parent folder of the file (e.g. "values" or "values-de")
        File file = context.file;
        File folder = file.getParentFile();

        if (folder == null) {
            return;
        }

        // Get or create the map for this folder
        Map<String, Location> resourceMap = mFolderToResourceMap.get(folder);
        if (resourceMap == null) {
            resourceMap = new HashMap<>();
            mFolderToResourceMap.put(folder, resourceMap);
        }

        // Check if this resource has already been defined in the same folder
        Location existingLocation = resourceMap.get(key);
        if (existingLocation != null) {
            // We have a duplicate — but only report if it's in the same file
            // or in the same folder (different files in same folder also counts)
            Location location = context.getLocation(element);
            String message = String.format(
                    "`%1$s` has already been defined in this folder",
                    name);

            // Attach secondary location pointing to the first definition
            location.setSecondary(existingLocation);
            existingLocation.setMessage("Previously defined here");

            context.report(ISSUE, element, location, message);
        } else {
            // Record the first definition
            Location location = context.getLocation(element);
            resourceMap.put(key, location);
        }
    }

    /**
     * Returns the resource type for a given XML element in a values file.
     */
    @Nullable
    private static String getResourceType(@NonNull Element element) {
        String tagName = element.getTagName();
        if (TAG_ITEM.equals(tagName)) {
            return element.getAttribute(ATTR_TYPE);
        }
        return tagName;
    }

    /**
     * Checks whether the given element is a styleable child item (attr inside
     * declare-styleable) — these can be legitimately duplicated.
     */
    private static boolean isStyleableAttr(@NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element) {
            String parentTag = ((Element) parent).getTagName();
            return "declare-styleable".equals(parentTag);
        }
        return false;
    }
}