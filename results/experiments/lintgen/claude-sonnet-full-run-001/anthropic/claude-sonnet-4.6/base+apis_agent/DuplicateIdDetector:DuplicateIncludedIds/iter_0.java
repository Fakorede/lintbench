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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.VIEW_INCLUDE;

/**
 * Checks for duplicate ids within a layout and across layouts that are
 * combined via include tags.
 */
public class DuplicateIdDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
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

    /** Within a single layout */
    public static final Issue WITHIN_LAYOUT = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `Activity#findViewById()` " +
            "can return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.FATAL,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from layout name to the set of ids defined in that layout (including
     * transitively included layouts).
     */
    private Map<String, Set<String>> mFileToIds;

    /**
     * Map from layout name to the list of layouts included by that layout.
     */
    private Map<String, List<String>> mIncludes;

    /**
     * Map from layout name to the location of each id defined in that layout.
     */
    private Map<String, Map<String, Location>> mFileToIdLocation;

    // Per-file state

    /** Ids found in the current file */
    private Set<String> mIds;

    /** Map from id to location in the current file */
    private Map<String, Location> mIdToLocation;

    /** Includes found in the current file */
    private List<String> mIncluded;

    /** Duplicate ids in the current file */
    private Map<String, Location> mDuplicates;

    /** Constructs a new {@link DuplicateIdDetector} */
    public DuplicateIdDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(VIEW_INCLUDE, ALL_ELEMENTS);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mFileToIdLocation = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashSet<>();
        mIdToLocation = new HashMap<>();
        mIncluded = new ArrayList<>();
        mDuplicates = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Store the ids and includes for this file
        String layoutName = getLayoutName(context.file);
        if (layoutName != null) {
            mFileToIds.put(layoutName, mIds);
            mFileToIdLocation.put(layoutName, mIdToLocation);
            if (!mIncluded.isEmpty()) {
                mIncludes.put(layoutName, mIncluded);
            }
        }

        mIds = null;
        mIdToLocation = null;
        mIncluded = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now check for cross-layout duplicate ids
        if (mIncludes.isEmpty()) {
            return;
        }

        // For each layout that includes other layouts, check if there are
        // duplicate ids across the included layouts
        // We need to do a graph traversal to find all transitively included ids

        // Build a map from layout name to all ids (including transitive includes)
        Map<String, Set<String>> allIds = new HashMap<>();
        // Track which layouts we've already processed to avoid cycles
        Set<String> processing = new HashSet<>();

        for (String layout : mFileToIds.keySet()) {
            computeAllIds(layout, allIds, processing, new HashSet<>());
        }

        // Now check each layout that has includes for duplicate ids
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            // Collect all ids from this layout and all included layouts
            Set<String> seen = new HashSet<>();
            Set<String> duplicates = new HashSet<>();

            // Start with ids in this layout
            Set<String> ownIds = mFileToIds.get(layout);
            if (ownIds != null) {
                seen.addAll(ownIds);
            }

            // Check each included layout
            for (String included : includes) {
                Set<String> includedIds = allIds.get(included);
                if (includedIds != null) {
                    for (String id : includedIds) {
                        if (!seen.add(id)) {
                            duplicates.add(id);
                        }
                    }
                }
            }

            if (!duplicates.isEmpty()) {
                // Report the duplicates
                reportCrossLayoutDuplicates(context, layout, includes, duplicates, allIds);
            }
        }
    }

    /**
     * Compute all ids (including transitively included layouts) for the given layout.
     */
    private Set<String> computeAllIds(
            String layout,
            Map<String, Set<String>> allIds,
            Set<String> processing,
            Set<String> visited) {
        if (allIds.containsKey(layout)) {
            return allIds.get(layout);
        }

        if (processing.contains(layout) || visited.contains(layout)) {
            // Cycle detected or already visited in this path
            Set<String> ownIds = mFileToIds.get(layout);
            return ownIds != null ? ownIds : Collections.<String>emptySet();
        }

        processing.add(layout);

        Set<String> result = new HashSet<>();
        Set<String> ownIds = mFileToIds.get(layout);
        if (ownIds != null) {
            result.addAll(ownIds);
        }

        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                Set<String> includedIds = computeAllIds(included, allIds, processing,
                        new HashSet<>(visited));
                result.addAll(includedIds);
            }
        }

        processing.remove(layout);
        allIds.put(layout, result);
        return result;
    }

    /**
     * Report cross-layout duplicate ids.
     */
    private void reportCrossLayoutDuplicates(
            Context context,
            String layout,
            List<String> includes,
            Set<String> duplicates,
            Map<String, Set<String>> allIds) {

        // For each duplicate id, find where it's defined
        for (String duplicateId : duplicates) {
            // Find all layouts that define this id
            List<String> definingLayouts = new ArrayList<>();

            // Check the main layout
            Set<String> ownIds = mFileToIds.get(layout);
            if (ownIds != null && ownIds.contains(duplicateId)) {
                definingLayouts.add(layout);
            }

            // Check included layouts (transitively)
            for (String included : includes) {
                Set<String> includedIds = allIds.get(included);
                if (includedIds != null && includedIds.contains(duplicateId)) {
                    definingLayouts.add(included);
                }
            }

            if (definingLayouts.size() >= 2) {
                // Build the error message
                StringBuilder sb = new StringBuilder();
                sb.append("Duplicate id `").append(duplicateId)
                        .append("`, defined or included multiple times in `")
                        .append(layout).append("`: ");

                // Find locations for each defining layout
                Location location = null;
                Location prev = null;

                for (int i = definingLayouts.size() - 1; i >= 0; i--) {
                    String defLayout = definingLayouts.get(i);
                    Map<String, Location> locationMap = mFileToIdLocation.get(defLayout);
                    if (locationMap != null) {
                        Location loc = locationMap.get(duplicateId);
                        if (loc != null) {
                            if (prev != null) {
                                loc.setSecondary(prev);
                            }
                            prev = loc;
                            location = loc;
                        }
                    }
                }

                if (location == null) {
                    // Try to find a file location
                    for (String defLayout : definingLayouts) {
                        Map<String, Location> locationMap = mFileToIdLocation.get(defLayout);
                        if (locationMap != null && locationMap.containsKey(duplicateId)) {
                            location = locationMap.get(duplicateId);
                            break;
                        }
                    }
                }

                sb.append(definingLayouts.toString());

                if (location != null) {
                    context.report(CROSS_LAYOUT, location, sb.toString());
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (tag.equals(VIEW_INCLUDE)) {
            // Record the include
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                mIncluded.add(includedLayout);
            }
        } else {
            // Check for id attribute
            Attr idAttr = element.getAttributeNode(ATTR_ID);
            if (idAttr != null) {
                String id = idAttr.getValue();
                if (id != null && !id.isEmpty()) {
                    if (mIds.contains(id)) {
                        // Duplicate within the same layout
                        if (mDuplicates == null) {
                            mDuplicates = new HashMap<>();
                        }
                        if (!mDuplicates.containsKey(id)) {
                            // Report the duplicate
                            Location location = context.getLocation(idAttr);
                            Location existing = mIdToLocation.get(id);
                            if (existing != null) {
                                location.setSecondary(existing);
                            }
                            context.report(WITHIN_LAYOUT, idAttr, location,
                                    String.format("Duplicate id `%1$s`, already defined earlier in this layout", id));
                            mDuplicates.put(id, location);
                        }
                    } else {
                        mIds.add(id);
                        mIdToLocation.put(id, context.getLocation(idAttr));
                    }
                }
            }
        }
    }

    /**
     * Returns the layout name (without extension) for the given file.
     */
    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }
}