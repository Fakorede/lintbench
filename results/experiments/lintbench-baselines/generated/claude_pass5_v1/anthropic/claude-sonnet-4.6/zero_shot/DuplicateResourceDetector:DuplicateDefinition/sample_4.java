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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

/**
 * Checks for duplicate resource definitions within the same resource folder.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue: duplicate definitions */
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
     * Map from resource folder (the parent directory, e.g. "values" or "values-de") to a map
     * of resource keys (type+name) to their first-seen location.
     */
    private final Map<File, Map<String, Location>> mFolderToResourceMap = new HashMap<>();

    /**
     * Map from resource folder to a list of (key, duplicate location, original location) triples
     * that need to be reported after all files have been visited.
     */
    private final Map<File, List<DuplicateEntry>> mPendingErrors = new HashMap<>();

    /** Holds information about a duplicate resource entry. */
    private static class DuplicateEntry {
        final String key;
        final Location duplicateLocation;
        final Location originalLocation;

        DuplicateEntry(String key, Location duplicateLocation, Location originalLocation) {
            this.key = key;
            this.duplicateLocation = duplicateLocation;
            this.originalLocation = originalLocation;
        }
    }

    /** Constructs a new {@link DuplicateResourceDetector}. */
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
        // We handle everything via visitElement; nothing special needed here.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We only care about top-level resource items (direct children of the root <resources>)
        Node parent = element.getParentNode();
        if (parent == null) {
            return;
        }
        if (parent.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        // Must be a direct child of the root element
        if (parent.getParentNode() == null ||
                parent.getParentNode().getNodeType() != Node.DOCUMENT_NODE) {
            return;
        }

        String tagName = element.getTagName();

        // Determine the resource type and name
        String type;
        String name;

        if (TAG_ITEM.equals(tagName)) {
            // <item type="..." name="...">
            type = element.getAttribute(ATTR_TYPE);
            name = element.getAttribute(ATTR_NAME);
            if (type == null || type.isEmpty() || name == null || name.isEmpty()) {
                return;
            }
        } else {
            // e.g. <string name="...">, <color name="...">, <dimen name="...">, etc.
            name = element.getAttribute(ATTR_NAME);
            if (name == null || name.isEmpty()) {
                return;
            }
            type = tagName;
        }

        // Normalize the name (replace dots and colons with underscores, as AAPT does)
        name = name.replace('.', '_').replace(':', '_');

        // Build a key that combines type and name
        String key = type + "/" + name;

        // The resource folder is the parent directory of the file being processed
        File resourceFolder = context.file.getParentFile();
        if (resourceFolder == null) {
            return;
        }

        Map<String, Location> resourceMap = mFolderToResourceMap.get(resourceFolder);
        if (resourceMap == null) {
            resourceMap = new HashMap<>();
            mFolderToResourceMap.put(resourceFolder, resourceMap);
        }

        Location location = context.getLocation(element);

        if (resourceMap.containsKey(key)) {
            // Duplicate found – record it for reporting
            Location originalLocation = resourceMap.get(key);
            List<DuplicateEntry> errors = mPendingErrors.get(resourceFolder);
            if (errors == null) {
                errors = new ArrayList<>();
                mPendingErrors.put(resourceFolder, errors);
            }
            errors.add(new DuplicateEntry(key, location, originalLocation));
        } else {
            resourceMap.put(key, location);
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // We report errors after visiting each file so that we have complete location info.
        // However, since we need cross-file duplicate detection within the same folder,
        // we defer reporting to afterCheckProject. Nothing to do here.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Report all pending errors
        for (Map.Entry<File, List<DuplicateEntry>> folderEntry : mPendingErrors.entrySet()) {
            for (DuplicateEntry entry : folderEntry.getValue()) {
                String resourceName = entry.key;
                // Make a secondary location pointing to the original definition
                Location secondary = entry.originalLocation;
                secondary = Location.create(secondary.getFile(), secondary.getStart(), secondary.getEnd());
                secondary.setMessage("Originally defined here");

                Location primary = entry.duplicateLocation;
                primary.setSecondary(secondary);

                String message = String.format(
                        "`%1$s` has already been defined in this folder",
                        resourceName);
                // We need a context to report – use the location's file
                context.report(ISSUE, primary, message);
            }
        }
    }
}