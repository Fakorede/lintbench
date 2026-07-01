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

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.VIEW_INCLUDE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for duplicate ids within a layout and across layouts that are combined
 * via include tags.
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
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from layout name (without .xml) to the set of ids defined in that layout.
     * Populated during the file visiting phase.
     */
    private Map<String, Set<String>> mFileToIds;

    /**
     * Map from layout name to the list of layouts it includes.
     * Populated during the file visiting phase.
     */
    private Map<String, List<String>> mIncludes;

    /**
     * Map from layout name to the list of locations for each id defined in that layout.
     * Used for error reporting.
     */
    private Map<String, Map<String, Location>> mFileToIdLocation;

    /** Creates a new {@link DuplicateIdDetector} */
    public DuplicateIdDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mFileToIdLocation = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for duplicate ids within the same layout file
        if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", ATTR_ID)) {
            Attr idAttr = element.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_ID);
            String id = idAttr.getValue();

            String layoutName = getLayoutName(context.file);

            // Track ids for cross-layout duplicate checking
            Set<String> ids = mFileToIds.get(layoutName);
            if (ids == null) {
                ids = new HashSet<>();
                mFileToIds.put(layoutName, ids);
            }

            Map<String, Location> locationMap = mFileToIdLocation.get(layoutName);
            if (locationMap == null) {
                locationMap = new HashMap<>();
                mFileToIdLocation.put(layoutName, locationMap);
            }

            if (ids.contains(id)) {
                // Duplicate within the same layout
                Location location = context.getLocation(idAttr);
                Location previousLocation = locationMap.get(id);
                if (previousLocation != null) {
                    location.setSecondary(previousLocation);
                    previousLocation.setMessage("Duplicate id `" + id + "` originally defined here");
                }
                context.report(WITHIN_LAYOUT, idAttr, location,
                        "Duplicate id `" + id + "`, already defined earlier in this layout");
            } else {
                ids.add(id);
                locationMap.put(id, context.getLocation(idAttr));
            }
        }

        // Track includes
        if (VIEW_INCLUDE.equals(element.getTagName())) {
            String layoutAttr = element.getAttribute(ATTR_LAYOUT);
            if (layoutAttr != null && layoutAttr.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layoutAttr.substring(LAYOUT_RESOURCE_PREFIX.length());
                String currentLayout = getLayoutName(context.file);

                List<String> includes = mIncludes.get(currentLayout);
                if (includes == null) {
                    includes = new ArrayList<>();
                    mIncludes.put(currentLayout, includes);
                }
                includes.add(includedLayout);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes.isEmpty()) {
            return;
        }

        // For each layout that includes other layouts, check for duplicate ids
        // across the include chain
        for (String layout : mIncludes.keySet()) {
            checkLayout(context, layout, new ArrayDeque<>(), new HashSet<>());
        }
    }

    /**
     * Recursively checks a layout and all its included layouts for duplicate ids.
     *
     * @param context the lint context
     * @param layout the layout name to check
     * @param chain the current include chain (for cycle detection)
     * @param visited already visited layouts (for cycle detection)
     */
    private void checkLayout(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull Deque<String> chain,
            @NonNull Set<String> visited) {

        if (visited.contains(layout)) {
            return;
        }

        List<String> includes = mIncludes.get(layout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        chain.push(layout);
        visited.add(layout);

        for (String included : includes) {
            // Collect all ids in the current layout
            Set<String> currentIds = mFileToIds.get(layout);
            if (currentIds == null) {
                currentIds = Collections.emptySet();
            }

            // Collect all ids in the included layout
            Set<String> includedIds = mFileToIds.get(included);
            if (includedIds != null) {
                // Find duplicates
                for (String id : includedIds) {
                    if (currentIds.contains(id)) {
                        // Found a duplicate id across layouts
                        reportCrossLayoutDuplicate(context, layout, included, id, chain);
                    }
                }
            }

            // Also check transitively included layouts
            if (!visited.contains(included)) {
                // Collect all ids reachable from the current layout
                Set<String> allCurrentIds = collectAllIds(layout, new HashSet<>());

                checkTransitiveIncludes(context, layout, included, allCurrentIds,
                        new HashSet<>(visited), chain);
            }
        }

        chain.pop();
    }

    /**
     * Collects all ids defined in a layout and all its transitively included layouts.
     */
    private Set<String> collectAllIds(String layout, Set<String> visitedLayouts) {
        if (visitedLayouts.contains(layout)) {
            return Collections.emptySet();
        }
        visitedLayouts.add(layout);

        Set<String> result = new HashSet<>();
        Set<String> ids = mFileToIds.get(layout);
        if (ids != null) {
            result.addAll(ids);
        }

        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                result.addAll(collectAllIds(included, visitedLayouts));
            }
        }

        return result;
    }

    /**
     * Checks for duplicate ids between a parent layout and a transitively included layout.
     */
    private void checkTransitiveIncludes(
            @NonNull Context context,
            @NonNull String rootLayout,
            @NonNull String currentIncluded,
            @NonNull Set<String> parentIds,
            @NonNull Set<String> visited,
            @NonNull Deque<String> chain) {

        if (visited.contains(currentIncluded)) {
            return;
        }
        visited.add(currentIncluded);

        List<String> subIncludes = mIncludes.get(currentIncluded);
        if (subIncludes == null) {
            return;
        }

        for (String subIncluded : subIncludes) {
            Set<String> subIds = mFileToIds.get(subIncluded);
            if (subIds != null) {
                for (String id : subIds) {
                    if (parentIds.contains(id)) {
                        reportCrossLayoutDuplicate(context, rootLayout, subIncluded, id, chain);
                    }
                }
            }

            checkTransitiveIncludes(context, rootLayout, subIncluded, parentIds,
                    new HashSet<>(visited), chain);
        }
    }

    /**
     * Reports a duplicate id found across layouts connected by include tags.
     */
    private void reportCrossLayoutDuplicate(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull String includedLayout,
            @NonNull String id,
            @NonNull Deque<String> chain) {

        // Find the file for the included layout
        File includedFile = findLayoutFile(context, includedLayout);
        File parentFile = findLayoutFile(context, layout);

        if (includedFile == null && parentFile == null) {
            return;
        }

        Location location;
        if (includedFile != null) {
            Map<String, Location> locationMap = mFileToIdLocation.get(includedLayout);
            if (locationMap != null && locationMap.containsKey(id)) {
                location = locationMap.get(id);
            } else {
                location = Location.create(includedFile);
            }
        } else {
            location = Location.create(parentFile);
        }

        // Build secondary location pointing to the parent layout
        if (parentFile != null) {
            Map<String, Location> parentLocationMap = mFileToIdLocation.get(layout);
            Location secondaryLocation = null;
            if (parentLocationMap != null && parentLocationMap.containsKey(id)) {
                secondaryLocation = parentLocationMap.get(id);
                secondaryLocation.setMessage("Duplicate id `" + id + "` defined here");
            } else {
                secondaryLocation = Location.create(parentFile);
                secondaryLocation.setMessage("Included from `" + layout + "`");
            }
            location.setSecondary(secondaryLocation);
        }

        String message = String.format(
                "Duplicate id `%1$s`, defined in layout `%2$s`, included in layout `%3$s`",
                id, includedLayout, layout);

        context.report(CROSS_LAYOUT, location, message);
    }

    /**
     * Finds the layout file for the given layout name.
     */
    @Nullable
    private File findLayoutFile(@NonNull Context context, @NonNull String layoutName) {
        // Try to find the file from the location maps
        Map<String, Location> locationMap = mFileToIdLocation.get(layoutName);
        if (locationMap != null && !locationMap.isEmpty()) {
            Location loc = locationMap.values().iterator().next();
            if (loc.getFile() != null) {
                return loc.getFile();
            }
        }

        // Try to find from the project's resource directories
        File resourceDir = context.getProject().getResourceFolders().isEmpty()
                ? null
                : context.getProject().getResourceFolders().get(0);

        if (resourceDir != null) {
            File layoutDir = new File(resourceDir, "layout");
            if (layoutDir.exists()) {
                File layoutFile = new File(layoutDir, layoutName + ".xml");
                if (layoutFile.exists()) {
                    return layoutFile;
                }
            }
        }

        return null;
    }

    /**
     * Returns the layout name (without extension) for the given file.
     */
    @NonNull
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }
}