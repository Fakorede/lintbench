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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for duplicate ids within a layout and across included layouts.
 */
public class DuplicateIdDetector extends LayoutDetector {

    /** The main issue: Duplicate ids within a single layout file */
    public static final Issue WITHIN_LAYOUT = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a single layout, id's should be unique since otherwise `findViewById()` " +
            "can return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Duplicate ids across layouts combined with include tags */
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

    /**
     * Map from layout name (without .xml) to the set of ids defined in that layout.
     * Used for cross-layout duplicate detection.
     */
    private Map<String, Set<String>> mFileToIds;

    /**
     * Map from layout name to the list of layouts it includes.
     */
    private Map<String, List<String>> mIncludes;

    /**
     * Map from id to the location where it was first defined (within the current file).
     * Used for within-layout duplicate detection.
     */
    private Map<String, Location> mIdToLocation;

    /**
     * Map from id to the list of locations where it is defined (within the current file).
     * Used for within-layout duplicate detection when there are duplicates.
     */
    private Map<String, List<Location>> mDuplicates;

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
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIdToLocation = new HashMap<>();
        mDuplicates = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Store the ids found in this file for cross-layout analysis
        XmlContext xmlContext = (XmlContext) context;
        String layoutName = getLayoutName(context.file);

        if (mFileToIds != null) {
            mFileToIds.put(layoutName, new HashSet<>(mIdToLocation.keySet()));
        }

        mIdToLocation = null;
        mDuplicates = null;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes.isEmpty()) {
            return;
        }

        // Now check for cross-layout duplicate ids by traversing include chains
        // For each layout that includes other layouts, collect all ids transitively
        // and check for duplicates.

        // Build a graph and detect duplicates in include chains
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            // For each layout, collect all ids transitively
            Set<String> visited = new HashSet<>();
            checkIncludeChain(context, layout, includes, visited, new ArrayList<String>());
        }
    }

    /**
     * Recursively checks include chains for duplicate ids.
     */
    private void checkIncludeChain(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull List<String> includes,
            @NonNull Set<String> visited,
            @NonNull List<String> chain) {

        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        Set<String> idsInLayout = mFileToIds.get(layout);
        if (idsInLayout == null) {
            idsInLayout = Collections.emptySet();
        }

        // For each included layout, check if there are id conflicts
        for (String included : includes) {
            Set<String> idsInIncluded = getTransitiveIds(included, new HashSet<String>());
            if (idsInIncluded == null) {
                continue;
            }

            // Find intersection
            for (String id : idsInLayout) {
                if (idsInIncluded.contains(id)) {
                    // Report duplicate id across layouts
                    // Find the files that define this id
                    String message = String.format(
                            "Duplicate id `%1$s`, defined or included multiple times in `%2$s`: " +
                            "Included from `%3$s`",
                            id, layout + ".xml", included + ".xml");

                    context.report(
                            CROSS_LAYOUT,
                            Location.create(context.file),
                            message);
                }
            }
        }
    }

    /**
     * Returns all ids transitively reachable from the given layout via includes.
     */
    @NonNull
    private Set<String> getTransitiveIds(@NonNull String layout, @NonNull Set<String> seen) {
        if (seen.contains(layout)) {
            return Collections.emptySet();
        }
        seen.add(layout);

        Set<String> result = new HashSet<>();
        Set<String> ids = mFileToIds.get(layout);
        if (ids != null) {
            result.addAll(ids);
        }

        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                result.addAll(getTransitiveIds(included, seen));
            }
        }

        return result;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        // Handle include tags - record the include relationship
        if (VIEW_INCLUDE.equals(tagName)) {
            String layoutAttr = element.getAttribute(ATTR_LAYOUT);
            if (layoutAttr != null && layoutAttr.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layoutAttr.substring(LAYOUT_RESOURCE_PREFIX.length());
                String currentLayout = getLayoutName(context.file);

                if (mIncludes != null) {
                    List<String> includes = mIncludes.get(currentLayout);
                    if (includes == null) {
                        includes = new ArrayList<>();
                        mIncludes.put(currentLayout, includes);
                    }
                    includes.add(includedLayout);
                }
            }
        }

        // Check for duplicate ids within the current layout
        Attr idAttr = element.getAttributeNode(ATTR_ID);
        if (idAttr == null) {
            // Try with android namespace
            idAttr = element.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android", "id");
        }

        if (idAttr != null) {
            String id = idAttr.getValue();
            if (id != null && !id.isEmpty()) {
                if (mIdToLocation != null) {
                    if (mIdToLocation.containsKey(id)) {
                        // Duplicate id within the same layout
                        if (mDuplicates == null) {
                            mDuplicates = new HashMap<>();
                        }
                        List<Location> locations = mDuplicates.get(id);
                        if (locations == null) {
                            locations = new ArrayList<>();
                            // Add the first occurrence
                            locations.add(mIdToLocation.get(id));
                            mDuplicates.put(id, locations);
                        }
                        Location location = context.getLocation(idAttr);
                        locations.add(location);

                        // Report the duplicate
                        String message = String.format(
                                "Duplicate id `%1$s`, defined multiple times in this layout",
                                id);

                        Location secondary = mIdToLocation.get(id);
                        if (secondary != null) {
                            secondary.setMessage("Defined here");
                        }

                        context.report(WITHIN_LAYOUT, idAttr, location, message);
                    } else {
                        mIdToLocation.put(id, context.getLocation(idAttr));

                        // Also store in the file-to-ids map for cross-layout analysis
                        if (mFileToIds != null) {
                            String layoutName = getLayoutName(context.file);
                            Set<String> ids = mFileToIds.get(layoutName);
                            if (ids == null) {
                                ids = new HashSet<>();
                                mFileToIds.put(layoutName, ids);
                            }
                            ids.add(id);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the layout name (without directory prefix and .xml extension) for the given file.
     */
    @NonNull
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".xml")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }
}