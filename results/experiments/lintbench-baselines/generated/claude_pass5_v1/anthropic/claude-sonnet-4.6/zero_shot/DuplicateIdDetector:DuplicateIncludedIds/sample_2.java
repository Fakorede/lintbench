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
import static com.android.SdkConstants.TAG_INCLUDE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

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
            "Within a layout, id's should be unique since otherwise `findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Map from layout resource name to the set of ids defined in that layout */
    private final Map<String, Set<String>> mFileToIds = new HashMap<>();

    /** Map from layout resource name to the list of layouts it includes */
    private final Map<String, List<String>> mIncludes = new HashMap<>();

    /** Ids found in the current file */
    private Set<String> mIds;

    /** Ids in current file that have already been reported as duplicates */
    private Set<String> mReported;

    /** Locations for each id in the current file */
    private Map<String, Location> mIdToLocation;

    /** Includes in the current file */
    private List<String> mCurrentIncludes;

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
        mIds = new HashSet<>();
        mReported = new HashSet<>();
        mIdToLocation = new HashMap<>();
        mCurrentIncludes = new ArrayList<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Store the ids and includes for this file for cross-layout analysis
        String layoutName = getLayoutName(context);
        if (layoutName != null) {
            mFileToIds.put(layoutName, mIds);
            if (!mCurrentIncludes.isEmpty()) {
                mIncludes.put(layoutName, mCurrentIncludes);
            }
        }

        mIds = null;
        mReported = null;
        mIdToLocation = null;
        mCurrentIncludes = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes.isEmpty()) {
            return;
        }

        // Now look for include chains where the same id is defined in multiple layouts
        // For each layout that has includes, compute the transitive set of ids
        // and check for duplicates

        // We need to find all layouts that include other layouts and check if
        // any ids are duplicated in the include chain.

        // Build a map of which layouts are included by other layouts (reverse map)
        // Then for each layout that includes others, gather all ids transitively
        // and report duplicates.

        // Process each layout that has includes
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            String layout = entry.getKey();
            List<String> includes = entry.getValue();

            // For each included layout, check if there are id conflicts
            Set<String> ownIds = mFileToIds.get(layout);
            if (ownIds == null) {
                ownIds = Collections.emptySet();
            }

            // Gather all ids from included layouts (transitively)
            // and check for conflicts with the parent layout's ids
            checkIncludeChain(context, layout, ownIds, includes,
                    new HashSet<>(), new ArrayList<>());
        }
    }

    /**
     * Recursively checks include chains for duplicate ids.
     */
    private void checkIncludeChain(
            @NonNull Context context,
            @NonNull String rootLayout,
            @NonNull Set<String> rootIds,
            @NonNull List<String> includes,
            @NonNull Set<String> visited,
            @NonNull List<String> chain) {

        for (String included : includes) {
            if (visited.contains(included)) {
                continue; // Avoid cycles
            }
            visited.add(included);

            Set<String> includedIds = mFileToIds.get(included);
            if (includedIds != null && !includedIds.isEmpty()) {
                // Check for conflicts with root layout's ids
                for (String id : includedIds) {
                    if (rootIds.contains(id)) {
                        // Found a duplicate id between root layout and included layout
                        // Report this as a warning
                        // We report on the context (project level check)
                        // Build message
                        String message = String.format(
                                "Duplicate id `%1$s`, defined or included multiple times in "
                                        + "layout `%2$s`: [%3$s]",
                                id,
                                rootLayout,
                                describeChain(chain, included));
                        context.report(
                                CROSS_LAYOUT,
                                Location.create(context.file),
                                message);
                    }
                }

                // Also check against other included layouts at the same level
                // by gathering all ids from the current chain and checking
            }

            // Recurse into this included layout's includes
            List<String> nestedIncludes = mIncludes.get(included);
            if (nestedIncludes != null) {
                List<String> newChain = new ArrayList<>(chain);
                newChain.add(included);
                checkIncludeChain(context, rootLayout, rootIds, nestedIncludes,
                        visited, newChain);
            }

            visited.remove(included);
        }
    }

    private static String describeChain(@NonNull List<String> chain, @NonNull String last) {
        if (chain.isEmpty()) {
            return last;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : chain) {
            sb.append(s).append(", ");
        }
        sb.append(last);
        return sb.toString();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for includes
        if (TAG_INCLUDE.equals(element.getTagName())) {
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                if (mCurrentIncludes != null) {
                    mCurrentIncludes.add(includedLayout);
                }
            }
        }

        // Check for duplicate ids within the current layout
        Attr idAttr = element.getAttributeNode(ATTR_ID);
        if (idAttr == null) {
            // Try with namespace
            idAttr = element.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android", "id");
        }

        if (idAttr != null) {
            String id = idAttr.getValue();
            if (id != null && !id.isEmpty()) {
                if (mIds != null) {
                    if (mIds.contains(id)) {
                        if (mReported != null && !mReported.contains(id)) {
                            mReported.add(id);
                            Location location = context.getLocation(idAttr);
                            Location previousLocation = mIdToLocation != null
                                    ? mIdToLocation.get(id) : null;
                            if (previousLocation != null) {
                                previousLocation.setMessage("Duplicate id " + id +
                                        " originally defined here");
                                location.setSecondary(previousLocation);
                            }
                            context.report(WITHIN_LAYOUT, idAttr, location,
                                    String.format(
                                            "Duplicate id `%1$s`, already defined earlier in this layout",
                                            id));
                        }
                    } else {
                        mIds.add(id);
                        if (mIdToLocation != null) {
                            mIdToLocation.put(id, context.getLocation(idAttr));
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the layout resource name for the given context (file name without extension).
     */
    @Nullable
    private static String getLayoutName(@NonNull Context context) {
        String name = context.file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }
}