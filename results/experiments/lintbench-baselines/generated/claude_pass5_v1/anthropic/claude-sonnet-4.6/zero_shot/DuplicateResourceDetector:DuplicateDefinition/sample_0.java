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
import com.android.utils.Pair;

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
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from resource folder (e.g. "res/values") to a map of resource keys
     * (type+name) to the location where the resource was first defined.
     */
    private final Map<File, Map<String, Location>> mFolderToResourceMap = new HashMap<>();

    /** Constructs a new {@link DuplicateResourceDetector} */
    public DuplicateResourceDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // We process element by element via visitElement, so nothing needed here.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // Only process top-level elements (direct children of the root <resources> element)
        Node parent = element.getParentNode();
        if (parent == null) {
            return;
        }
        if (parent.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        // Check that the parent is the root element (i.e., <resources>)
        if (parent.getParentNode() == null ||
                parent.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            // This element is nested more than one level deep; skip it.
            return;
        }

        // Determine the resource type and name
        String resourceType;
        String resourceName;

        if (TAG_ITEM.equals(tag)) {
            // <item type="..." name="...">
            resourceType = element.getAttribute(ATTR_TYPE);
            resourceName = element.getAttribute(ATTR_NAME);
            if (resourceType == null || resourceType.isEmpty()) {
                return;
            }
        } else {
            // e.g. <string name="...">, <dimen name="...">, etc.
            resourceType = tag;
            resourceName = element.getAttribute(ATTR_NAME);
        }

        if (resourceName == null || resourceName.isEmpty()) {
            return;
        }

        // Build a unique key for this resource
        String key = resourceType + "/" + resourceName;

        // The folder containing this resource file (e.g. "res/values" or "res/values-de")
        File folder = context.file.getParentFile();

        Map<String, Location> resourceMap = mFolderToResourceMap.get(folder);
        if (resourceMap == null) {
            resourceMap = new HashMap<>();
            mFolderToResourceMap.put(folder, resourceMap);
        }

        Location existingLocation = resourceMap.get(key);
        if (existingLocation != null) {
            // We have a duplicate!
            Location location = context.getLocation(element);
            location.setSecondary(existingLocation);
            existingLocation.setMessage("Previously defined here");

            context.report(
                    ISSUE,
                    element,
                    location,
                    String.format("`%1$s` has already been defined in this folder", key));
        } else {
            // Record the location of this resource definition
            resourceMap.put(key, context.getLocation(element));
        }
    }
}