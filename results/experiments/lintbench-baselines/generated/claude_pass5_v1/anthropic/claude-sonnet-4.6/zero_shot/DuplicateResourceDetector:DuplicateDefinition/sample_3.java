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
import com.android.tools.lint.detector.api.LintUtils;
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
     * Map from resource folder (e.g. "res/values") to a map from resource key
     * (e.g. "string/app_name") to the location where it was first defined.
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
                "plurals",
                "integer",
                "integer-array",
                "bool",
                "dimen",
                "color",
                "style",
                "declare-styleable",
                "attr",
                "item",
                "drawable",
                "layout",
                "fraction",
                "array"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Get the resource name attribute
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }

        String name = nameAttr.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        String tagName = element.getTagName();

        // For <item> elements, the type attribute defines the resource type
        String resourceType;
        if ("item".equals(tagName)) {
            Attr typeAttr = element.getAttributeNode("type");
            if (typeAttr == null) {
                return;
            }
            resourceType = typeAttr.getValue();
            if (resourceType == null || resourceType.isEmpty()) {
                return;
            }
        } else {
            resourceType = tagName;
        }

        // Build a unique key: type/name
        String resourceKey = resourceType + "/" + name;

        // Get the folder path to use as the scope key
        // We use the parent folder (e.g. "res/values" or "res/values-de")
        File file = context.file;
        File folder = file.getParentFile();
        String folderKey = folder != null ? folder.getPath() : file.getPath();

        // Get or create the map for this folder
        Map<String, Location> resourceMap = mFolderToResourceMap.get(folderKey);
        if (resourceMap == null) {
            resourceMap = new HashMap<String, Location>();
            mFolderToResourceMap.put(folderKey, resourceMap);
        }

        if (resourceMap.containsKey(resourceKey)) {
            // Duplicate found!
            Location previousLocation = resourceMap.get(resourceKey);
            Location location = context.getLocation(nameAttr);

            if (previousLocation != null) {
                location.setSecondary(previousLocation);
                previousLocation.setMessage("Previously defined here");
            }

            context.report(
                    ISSUE,
                    element,
                    location,
                    String.format("`%1$s` has already been defined in this folder", resourceKey));
        } else {
            // Record the location of this definition
            Location location = context.getLocation(nameAttr);
            resourceMap.put(resourceKey, location);
        }
    }
}