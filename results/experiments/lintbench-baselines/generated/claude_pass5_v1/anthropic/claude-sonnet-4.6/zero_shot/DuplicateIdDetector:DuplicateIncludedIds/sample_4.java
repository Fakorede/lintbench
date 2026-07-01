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
     * Map from layout name (without .xml) to the set of ids defined in that layout,
     * as well as the set of layouts included from that layout.
     */
    private Map<String, Set<String>> mFileToIds;

    /**
     * Map from layout name to the list of layouts it includes.
     */
    private Map<String, List<String>> mIncludes;

    /**
     * Map from layout name to the ids defined directly in that layout (not via includes).
     */
    private Map<String, List<String>> mFileToIdList;

    /**
     * Map from id to the location in the file where it is defined.
     * Only used within a single file pass.
     */
    private Map<String, Location> mIds;

    /**
     * Map from layout name to a map of id -> location for ids defined in that layout.
     */
    private Map<String, Map<String, Location>> mFileToIdLocation;

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
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mFileToIdList = new HashMap<>();
        mFileToIdLocation = new HashMap<>();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIds = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Store the ids found in this file
        String layoutName = getLayoutName(context.file);
        if (layoutName != null) {
            // Store all ids found in this layout
            Set<String> ids = new HashSet<>(mIds.keySet());
            mFileToIds.put(layoutName, ids);
            mFileToIdLocation.put(layoutName, new HashMap<>(mIds));

            // Store just the list of ids (for ordered processing)
            List<String> idList = new ArrayList<>(mIds.keySet());
            mFileToIdList.put(layoutName, idList);
        }
        mIds = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (context.getPhase() == 1) {
            // Compute the transitive include graph and check for duplicate ids
            // across included layouts
            checkCrossLayoutDuplicates(context);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_INCLUDE.equals(tagName)) {
            // Record the include
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                String currentLayout = getLayoutName(context.file);
                if (currentLayout != null) {
                    List<String> includes = mIncludes.get(currentLayout);
                    if (includes == null) {
                        includes = new ArrayList<>();
                        mIncludes.put(currentLayout, includes);
                    }
                    if (!includes.contains(includedLayout)) {
                        includes.add(includedLayout);
                    }
                }
            }
        }

        // Check for duplicate ids within this file
        Attr idAttr = element.getAttributeNode(ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            if (id != null && !id.isEmpty()) {
                if (mIds != null) {
                    if (mIds.containsKey(id)) {
                        // Duplicate id within this layout
                        Location location = context.getLocation(idAttr);
                        Location previousLocation = mIds.get(id);
                        String message = String.format(
                                "Duplicate id `%1$s`, already defined earlier in this layout",
                                id);
                        if (previousLocation != null) {
                            previousLocation.setMessage("Defined here");
                            location.setSecondary(previousLocation);
                        }
                        context.report(WITHIN_LAYOUT, idAttr, location, message);
                    } else {
                        mIds.put(id, context.getLocation(idAttr));
                    }
                }
            }
        }
    }

    /**
     * Returns the layout name (without .xml extension) for the given file.
     */
    @Nullable
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".xml")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    /**
     * After all files have been processed, check for duplicate ids across
     * layouts that are combined via include tags.
     */
    private void checkCrossLayoutDuplicates(@NonNull Context context) {
        // For each layout that has includes, compute the set of all ids
        // reachable via includes and check for duplicates.

        // We need to find all layouts that are "root" layouts (not included by anyone)
        // or just check all layouts that have includes.

        // Build a set of all layouts that are included by someone
        Set<String> includedLayouts = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : mIncludes.entrySet()) {
            includedLayouts.addAll(entry.getValue());
        }

        // Process all layouts that include other layouts
        for (String layout : mIncludes.keySet()) {
            // Do a DFS/BFS from this layout, collecting all ids
            // and checking for duplicates
            checkIncludeChain(context, layout, new ArrayDeque<>(), new HashSet<>());
        }
    }

    /**
     * Recursively checks a chain of included layouts for duplicate ids.
     *
     * @param context the lint context
     * @param layout the current layout being checked
     * @param visitStack stack to detect cycles
     * @param visited set of already-visited layouts in this chain
     */
    private void checkIncludeChain(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull Deque<String> visitStack,
            @NonNull Set<String> visited) {
        // Collect all ids reachable from this layout (including via includes)
        // and report duplicates
        Map<String, String> idToLayout = new HashMap<>(); // id -> first layout that defined it
        collectAndCheckIds(context, layout, idToLayout, new HashSet<>(), new ArrayDeque<>());
    }

    /**
     * Collects all ids from the given layout and its transitive includes,
     * reporting duplicates.
     *
     * @param context the lint context
     * @param layout the layout to process
     * @param idToLayout map from id to the layout that first defined it
     * @param visitedLayouts set of layouts already visited (to avoid cycles)
     * @param includeStack stack of includes leading to this layout
     */
    private void collectAndCheckIds(
            @NonNull Context context,
            @NonNull String layout,
            @NonNull Map<String, String> idToLayout,
            @NonNull Set<String> visitedLayouts,
            @NonNull Deque<String> includeStack) {

        if (visitedLayouts.contains(layout)) {
            return;
        }
        visitedLayouts.add(layout);
        includeStack.push(layout);

        // Get ids defined directly in this layout
        List<String> ids = mFileToIdList.get(layout);
        if (ids != null) {
            Map<String, Location> locationMap = mFileToIdLocation.get(layout);
            for (String id : ids) {
                if (idToLayout.containsKey(id)) {
                    // Duplicate id found across includes
                    String previousLayout = idToLayout.get(id);
                    reportCrossLayoutDuplicate(context, id, layout, previousLayout,
                            new ArrayList<>(includeStack));
                } else {
                    idToLayout.put(id, layout);
                }
            }
        }

        // Process included layouts
        List<String> includes = mIncludes.get(layout);
        if (includes != null) {
            for (String included : includes) {
                collectAndCheckIds(context, included, idToLayout, visitedLayouts, includeStack);
            }
        }

        includeStack.pop();
    }

    /**
     * Reports a cross-layout duplicate id.
     */
    private void reportCrossLayoutDuplicate(
            @NonNull Context context,
            @NonNull String id,
            @NonNull String currentLayout,
            @NonNull String previousLayout,
            @NonNull List<String> includeStack) {

        // Find the location of the id in the current layout
        Map<String, Location> currentLocations = mFileToIdLocation.get(currentLayout);
        Map<String, Location> previousLocations = mFileToIdLocation.get(previousLayout);

        Location location = null;
        if (currentLocations != null) {
            location = currentLocations.get(id);
        }

        Location previousLocation = null;
        if (previousLocations != null) {
            previousLocation = previousLocations.get(id);
        }

        String message = String.format(
                "Duplicate id `%1$s`, defined or included in layout `%2$s`, "
                        + "included from layout `%3$s`",
                id, previousLayout, currentLayout);

        if (location == null) {
            // Try to use file-level location
            location = guessLocation(context, currentLayout);
        }

        if (location != null) {
            if (previousLocation != null) {
                previousLocation.setMessage(
                        String.format("`%1$s` originally defined here", id));
                location.setSecondary(previousLocation);
            }
            context.report(CROSS_LAYOUT, location, message);
        } else if (previousLocation != null) {
            context.report(CROSS_LAYOUT, previousLocation, message);
        }
    }

    /**
     * Attempts to find a file-level location for the given layout name.
     */
    @Nullable
    private static Location guessLocation(@NonNull Context context, @NonNull String layoutName) {
        // Try to find the file in the project
        File projectDir = context.getProject().getDir();
        File resDir = new File(projectDir, "res");
        if (resDir.exists()) {
            File[] children = resDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.getName().startsWith("layout")) {
                        File layoutFile = new File(child, layoutName + ".xml");
                        if (layoutFile.exists()) {
                            return Location.create(layoutFile);
                        }
                    }
                }
            }
        }
        return null;
    }
}