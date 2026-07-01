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
import com.android.tools.lint.detector.api.LintFix;
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
 * Checks for duplicate resource definitions within the same resource folder.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definition",
            "You can define a resource multiple times in different resource folders; that's how " +
            "string translations are done, for example. However, defining the same resource " +
            "more than once in the same resource folder is likely an error, for example " +
            "attempting to add a new resource without realizing that the name is already used, " +
            "and so on.",
            Category.CORRECTNESS,
            6,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    /**
     * Map from resource folder (e.g. "res/values") to a map of resource keys
     * (type+name) to the location where they were first defined.
     */
    private final Map<String, Map<String, Location>> mFolderToResourceMap = new HashMap<>();

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
                "plurals",
                "integer",
                "integer-array",
                "bool",
                "dimen",
                "color",
                "style",
                "declare-styleable",
                "attr",
                "array",
                "drawable",
                "item",
                "layout",
                "menu",
                "fraction",
                "id"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Get the resource name from the "name" attribute
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Determine the resource type
        String tagName = element.getTagName();
        String resourceType = getResourceType(tagName, element);
        if (resourceType == null) {
            return;
        }

        // Build a unique key for this resource: type + name
        String resourceKey = resourceType + ":" + name;

        // Get the folder path (parent directory of the file)
        File file = context.file;
        File folder = file.getParentFile();
        if (folder == null) {
            return;
        }
        String folderPath = folder.getPath();

        // Look up or create the map for this folder
        Map<String, Location> resourceMap = mFolderToResourceMap.get(folderPath);
        if (resourceMap == null) {
            resourceMap = new HashMap<>();
            mFolderToResourceMap.put(folderPath, resourceMap);
        }

        Location currentLocation = context.getLocation(nameAttr);

        if (resourceMap.containsKey(resourceKey)) {
            // Duplicate found!
            Location previousLocation = resourceMap.get(resourceKey);

            // Create a secondary location pointing to the previous definition
            Location secondary = previousLocation;
            secondary = Location.create(
                    previousLocation.getFile(),
                    previousLocation.getStart(),
                    previousLocation.getEnd());
            secondary.setMessage("Previously defined here");

            Location primaryLocation = currentLocation;
            primaryLocation.setSecondary(secondary);

            context.report(
                    ISSUE,
                    element,
                    primaryLocation,
                    String.format(
                            "`%1$s` has already been defined in this folder",
                            name));
        } else {
            resourceMap.put(resourceKey, currentLocation);
        }
    }

    /**
     * Returns the resource type string for the given XML element tag name.
     *
     * @param tagName the XML element tag name
     * @param element the XML element
     * @return the resource type string, or null if not applicable
     */
    @Nullable
    private static String getResourceType(@NonNull String tagName, @NonNull Element element) {
        switch (tagName) {
            case "string":
                return "string";
            case "string-array":
                return "array";
            case "plurals":
                return "plurals";
            case "integer":
                return "integer";
            case "integer-array":
                return "array";
            case "bool":
                return "bool";
            case "dimen":
                return "dimen";
            case "color":
                return "color";
            case "style":
                return "style";
            case "declare-styleable":
                return "styleable";
            case "attr":
                return "attr";
            case "array":
                return "array";
            case "drawable":
                return "drawable";
            case "fraction":
                return "fraction";
            case "id":
                return "id";
            case "item": {
                // <item> elements have a "type" attribute that specifies the resource type
                Attr typeAttr = element.getAttributeNode("type");
                if (typeAttr != null) {
                    return typeAttr.getValue();
                }
                return null;
            }
            case "layout":
                return "layout";
            case "menu":
                return "menu";
            default:
                return null;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Nothing needed here; we accumulate state across files in the same folder
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Clean up state
        mFolderToResourceMap.clear();
    }
}