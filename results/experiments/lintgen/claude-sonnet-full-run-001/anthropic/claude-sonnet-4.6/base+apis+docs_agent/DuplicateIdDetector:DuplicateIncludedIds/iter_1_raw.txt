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
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_INCLUDE;

/**
 * Checks for duplicate ids within a layout and across included layouts.
 */
public class DuplicateIdDetector extends LayoutDetector {

    public static final Issue CROSS_LAYOUT = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if " +
            "layouts are combined with include tags, then the id's need to be unique " +
            "within any chain of included layouts, or `Activity#findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    /** Map from layout name to the set of ids defined in that layout */
    private final Map<String, Set<String>> mFileToIds = new HashMap<>();

    /** Map from layout name to the list of layouts it includes */
    private final Map<String, List<String>> mIncludes = new HashMap<>();

    /** Map from layout name to the location of each id defined in that layout */
    private final Map<String, Map<String, Location>> mFileToIdLocation = new HashMap<>();

    /** Map from layout name to the location of each include tag */
    private final Map<String, Map<String, Location>> mFileToIncludeLocation = new HashMap<>();

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_INCLUDE, "*");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        String layoutName = getLayoutName(context.file);

        if (TAG_INCLUDE.equals(tag)) {
            // Record the include
            Attr layoutAttr = element.getAttributeNode(ATTR_LAYOUT);
            if (layoutAttr != null) {
                String includedLayout = layoutAttr.getValue();
                if (includedLayout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                    includedLayout = includedLayout.substring(LAYOUT_RESOURCE_PREFIX.length());
                }

                List<String> includes = mIncludes.get(layoutName);
                if (includes == null) {
                    includes = new ArrayList<>();
                    mIncludes.put(layoutName, includes);
                }
                includes.add(includedLayout);

                // Record location of include
                Map<String, Location> includeLocations = mFileToIncludeLocation.get(layoutName);
                if (includeLocations == null) {
                    includeLocations = new HashMap<>();
                    mFileToIncludeLocation.put(layoutName, includeLocations);
                }
                includeLocations.put(includedLayout, context.getLocation(element));
            }
        } else {
            // Record the id if present
            Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
            if (idAttr != null) {
                String id = idAttr.getValue();
                // Normalize the id (strip @+id/ or @id/ prefix)
                if (id.startsWith("@+id/")) {
                    id = id.substring("@+id/".length());
                } else if (id.startsWith("@id/")) {
                    id = id.substring("@id/".length());
                }

                Set<String> ids = mFileToIds.get(layoutName);
                if (ids == null) {
                    ids = new HashSet<>();
                    mFileToIds.put(layoutName, ids);
                }
                ids.add(id);

                // Record location
                Map<String, Location> idLocations = mFileToIdLocation.get(layoutName);
                if (idLocations == null) {
                    idLocations = new HashMap<>();
                    mFileToIdLocation.put(layoutName, idLocations);
                }
                if (!idLocations.containsKey(id)) {
                    idLocations.put(id, context.getLocation(idAttr));
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Build set of all known layouts
        Set<String> allLayouts = new HashSet<>(mFileToIds.keySet());
        allLayouts.addAll(mIncludes.keySet());

        // Find layouts that have includes - these are the ones we need to check
        // We check from every layout that has includes
        for (String layout : new HashSet<>(mIncludes.keySet())) {
            // For each layout that includes others, check the full include chain
            // starting from this layout for duplicate ids
            checkIncludeChain(context, layout);
        }
    }

    /**
     * Check a layout's include chain for duplicate ids.
     * We do a DFS and collect all ids reachable from this layout,
     * reporting duplicates when an id is seen more than once.
     */
    private void checkIncludeChain(@NonNull Context context, @NonNull String rootLayout) {
        // We'll do a DFS, tracking the current path and accumulated ids
        List<String> path = new ArrayList<>();
        path.add(rootLayout);

        // Map from id -> layout where it was first encountered
        Map<String, String> seenIds = new HashMap<>();

        // First add ids from the root layout itself
        Set<String> rootIds = mFileToIds.get(rootLayout);
        if (rootIds != null) {
            for (String id : rootIds) {
                seenIds.put(id, rootLayout);
            }
        }

        // Now traverse includes
        List<String> includes = mIncludes.get(rootLayout);
        if (includes != null) {
            Set<String> visited = new HashSet<>();
            visited.add(rootLayout);
            for (String included : includes) {
                traverseIncludes(context, rootLayout, included, seenIds, path, visited);
            }
        }
    }

    private void traverseIncludes(
            @NonNull Context context,
            @NonNull String rootLayout,
            @NonNull String layout,
            @NonNull Map<String, String> seenIds,
            @NonNull List<String> path,
            @NonNull Set<String> visited) {

        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);
        path.add(layout);

        // Check ids in this layout against already-seen ids
        Set<String> ids = mFileToIds.get(layout);
        if (ids != null) {
            for (String id : ids) {
                if (seenIds.containsKey(id)) {
                    // Duplicate found!
                    String firstLayout = seenIds.get(id);
                    reportDuplicate(context, id, layout, firstLayout, rootLayout, path);
                } else {
                    seenIds.put(id, layout);
                }
            }
        }

        // Recurse into included layouts
        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                traverseIncludes(context, rootLayout, included, seenIds, path, visited);
            }
        }

        path.remove(path.size() - 1);
        visited.remove(layout);
    }

    private void reportDuplicate(
            @NonNull Context context,
            @NonNull String id,
            @NonNull String currentLayout,
            @NonNull String firstLayout,
            @NonNull String rootLayout,
            @NonNull List<String> path) {

        // Get location in current layout
        Location location = null;
        Map<String, Location> currentIdLocations = mFileToIdLocation.get(currentLayout);
        if (currentIdLocations != null) {
            location = currentIdLocations.get(id);
        }

        // Get location in first layout
        Location secondaryLocation = null;
        Map<String, Location> firstIdLocations = mFileToIdLocation.get(firstLayout);
        if (firstIdLocations != null) {
            secondaryLocation = firstIdLocations.get(id);
        }

        if (location == null) {
            return;
        }

        // Build include chain description
        StringBuilder chainDesc = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                chainDesc.append(" -> ");
            }
            chainDesc.append(path.get(i));
        }

        String message = String.format(
                "Duplicate id `@+id/%1$s`, defined or included multiple times in `%2$s`: "
                + "[%3$s] and [%4$s]",
                id, rootLayout, firstLayout, currentLayout);

        if (secondaryLocation != null) {
            secondaryLocation.setMessage("Defined here");
            location.setSecondary(secondaryLocation);
        }

        context.report(CROSS_LAYOUT, location, message);
    }

    /**
     * Returns the layout name (without extension) for a given file.
     */
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name;
    }
}