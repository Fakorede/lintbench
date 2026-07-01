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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_INCLUDE;

/**
 * Checks for duplicate ids within a layout and across layouts that are
 * combined via include tags.
 */
public class DuplicateIdDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue WITHIN_LAYOUT = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** The cross-layout issue discovered by this detector */
    public static final Issue CROSS_LAYOUT = Issue.create(
            "DuplicateIncludedIds",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if "
                    + "layouts are combined with include tags, then the id's need to be unique "
                    + "within any chain of included layouts, or `Activity#findViewById()` can "
                    + "return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    /** Map from layout resource name to the set of ids defined in that layout */
    private final Map<String, Set<String>> mFileToIds = new HashMap<>();

    /** Map from layout resource name to the list of included layouts */
    private final Map<String, List<String>> mIncludes = new HashMap<>();

    /** Map from layout resource name to its file */
    private final Map<String, Location> mFileLocations = new HashMap<>();

    /** Current file being analyzed */
    private Set<String> mIds;

    /** Current includes being analyzed */
    private List<String> mCurrentIncludes;

    /** Current file name (layout resource name without extension) */
    private String mCurrentLayoutName;

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashSet<>();
        mCurrentIncludes = null;
        File file = context.file;
        mCurrentLayoutName = file.getName();
        // Strip extension
        int dot = mCurrentLayoutName.lastIndexOf('.');
        if (dot != -1) {
            mCurrentLayoutName = mCurrentLayoutName.substring(0, dot);
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mFileToIds.put(mCurrentLayoutName, mIds);
        if (mCurrentIncludes != null && !mCurrentIncludes.isEmpty()) {
            mIncludes.put(mCurrentLayoutName, mCurrentIncludes);
        }
        mFileLocations.put(mCurrentLayoutName, Location.create(context.file));
        mIds = null;
        mCurrentIncludes = null;
        mCurrentLayoutName = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now check for cross-layout duplicates
        if (mIncludes.isEmpty()) {
            return;
        }

        // For each layout that includes other layouts, check for duplicate ids
        // We need to traverse the include graph and find all ids reachable from each root
        for (String layout : mIncludes.keySet()) {
            // Check this layout as a potential root
            checkLayout(context, layout, new ArrayList<>(), new HashSet<>());
        }
    }

    /**
     * Recursively checks for duplicate ids in a layout and all its included layouts.
     *
     * @param context   the lint context
     * @param layout    the layout resource name to check
     * @param chain     the current chain of includes leading to this layout
     * @param visited   set of already visited layouts to prevent infinite recursion
     */
    private void checkLayout(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull List<String> chain,
            @NonNull Set<String> visited) {

        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        List<String> includes = mIncludes.get(layout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        // Build a map of id -> layout for ids defined in the current layout
        Set<String> ownIds = mFileToIds.get(layout);
        if (ownIds == null) {
            ownIds = Collections.emptySet();
        }

        // For each included layout, gather all ids and check for duplicates with
        // ids already seen in this chain
        checkIncludeChain(context, layout, includes, ownIds, new ArrayList<>(chain),
                new HashSet<>(visited));
    }

    /**
     * Checks for duplicate ids across a layout and its includes.
     */
    private void checkIncludeChain(
            @NonNull Context context,
            @NonNull String rootLayout,
            @NonNull List<String> includes,
            @NonNull Set<String> rootIds,
            @NonNull List<String> chain,
            @NonNull Set<String> visited) {

        // Collect all ids from all included layouts (transitively)
        // and check for duplicates with the root layout's ids and each other

        // Map from id -> list of layouts that define it (within this include chain)
        Map<String, List<String>> idToLayouts = new HashMap<>();

        // Add root layout's ids
        for (String id : rootIds) {
            List<String> layouts = new ArrayList<>();
            layouts.add(rootLayout);
            idToLayouts.put(id, layouts);
        }

        // Now process each included layout
        for (String include : includes) {
            collectIds(include, idToLayouts, new HashSet<>());
        }

        // Report duplicates
        for (Map.Entry<String, List<String>> entry : idToLayouts.entrySet()) {
            List<String> layouts = entry.getValue();
            if (layouts.size() > 1) {
                String id = entry.getKey();
                // Build the error message
                StringBuilder sb = new StringBuilder();
                sb.append("Duplicate id ").append(id)
                        .append(", defined or included multiple times in ").append(rootLayout)
                        .append(": [");
                for (int i = 0; i < layouts.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(layouts.get(i));
                }
                sb.append("]");

                Location location = mFileLocations.get(rootLayout);
                if (location == null) {
                    location = Location.create(context.file);
                }

                // Build secondary locations
                Location secondary = null;
                for (int i = layouts.size() - 1; i >= 1; i--) {
                    String otherLayout = layouts.get(i);
                    Location otherLocation = mFileLocations.get(otherLayout);
                    if (otherLocation != null) {
                        otherLocation.setMessage("Defined here");
                        if (secondary != null) {
                            otherLocation.setSecondary(secondary);
                        }
                        secondary = otherLocation;
                    }
                }
                if (secondary != null) {
                    location.setSecondary(secondary);
                }

                context.report(CROSS_LAYOUT, location, sb.toString());
            }
        }
    }

    /**
     * Recursively collects all ids defined in a layout and its included layouts.
     *
     * @param layout       the layout to collect ids from
     * @param idToLayouts  map being populated: id -> list of layouts defining it
     * @param visited      set of visited layouts to prevent infinite recursion
     */
    private void collectIds(
            @NonNull String layout,
            @NonNull Map<String, List<String>> idToLayouts,
            @NonNull Set<String> visited) {

        if (visited.contains(layout)) {
            return;
        }
        visited.add(layout);

        Set<String> ids = mFileToIds.get(layout);
        if (ids != null) {
            for (String id : ids) {
                List<String> layouts = idToLayouts.get(id);
                if (layouts == null) {
                    layouts = new ArrayList<>();
                    idToLayouts.put(id, layouts);
                }
                if (!layouts.contains(layout)) {
                    layouts.add(layout);
                }
            }
        }

        // Recurse into included layouts
        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String include : includes) {
                collectIds(include, idToLayouts, visited);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (tag.equals(TAG_INCLUDE)) {
            // Record the include
            Attr layoutAttr = element.getAttributeNode(ATTR_LAYOUT);
            if (layoutAttr != null) {
                String value = layoutAttr.getValue();
                if (value.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                    String includedLayout = value.substring(LAYOUT_RESOURCE_PREFIX.length());
                    if (mCurrentIncludes == null) {
                        mCurrentIncludes = new ArrayList<>();
                    }
                    mCurrentIncludes.add(includedLayout);
                }
            }
        }

        // Check for duplicate ids within this layout
        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            if (id != null && !id.isEmpty()) {
                if (mIds != null) {
                    if (mIds.contains(id)) {
                        // Duplicate id within a single layout
                        context.report(
                                WITHIN_LAYOUT,
                                idAttr,
                                context.getLocation(idAttr),
                                String.format("Duplicate id `%1$s`, already defined earlier in this layout", id));
                    } else {
                        mIds.add(id);
                    }
                }
            }
        }
    }
}