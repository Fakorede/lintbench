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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

/**
 * Checks for duplicate resource definitions.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definition",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining " +
            "the same resource more than once in the same resource folder is likely " +
            "an error, for example attempting to add a new resource without realizing " +
            "that the name is already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from resource folder (e.g. "res/values") to a map of resource keys
     * (type+name) to the location where the resource was first defined.
     */
    private final Map<String, Map<String, Location>> mFolderToResourceMap =
            new HashMap<String, Map<String, Location>>();

    /** Constructs a new {@link DuplicateResourceDetector} */
    public DuplicateResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "string",
                "string-array",
                "integer",
                "integer-array",
                "bool",
                "dimen",
                "color",
                "style",
                "declare-styleable",
                "array",
                "plurals",
                "fraction",
                "attr",
                TAG_ITEM
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Get the name attribute
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
                // Try to infer from parent
                Node parent = element.getParentNode();
                if (parent instanceof Element) {
                    String parentTag = ((Element) parent).getTagName();
                    if ("string-array".equals(parentTag) || "integer-array".equals(parentTag)
                            || "array".equals(parentTag)) {
                        // Array items don't have individual names we track
                        return;
                    }
                }
                return;
            }
        } else {
            type = tagName;
        }

        // Build the resource key: type/name
        String resourceKey = type + "/" + name;

        // Get the folder path (parent directory of the file)
        File file = context.file;
        File folder = file.getParentFile();
        String folderPath = folder != null ? folder.getPath() : "";

        // Get or create the map for this folder
        Map<String, Location> resourceMap = mFolderToResourceMap.get(folderPath);
        if (resourceMap == null) {
            resourceMap = new HashMap<String, Location>();
            mFolderToResourceMap.put(folderPath, resourceMap);
        }

        // Check for duplicate
        Location existingLocation = resourceMap.get(resourceKey);
        if (existingLocation != null) {
            // Report the duplicate
            Location location = context.getLocation(element);
            location.setSecondary(existingLocation);
            existingLocation.setMessage("Previously defined here");
            context.report(
                    ISSUE,
                    element,
                    location,
                    String.format("`%1$s` has already been defined in this folder", name));
        } else {
            // Record this resource
            Location location = context.getLocation(element);
            resourceMap.put(resourceKey, location);
        }
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // We handle everything in visitElement
    }

    /**
     * Returns the folder key for a given file, using the resource folder name
     * (e.g. "values", "values-en") as the key component.
     */
    @NonNull
    private static String getFolderKey(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName();
        }
        return "";
    }
}