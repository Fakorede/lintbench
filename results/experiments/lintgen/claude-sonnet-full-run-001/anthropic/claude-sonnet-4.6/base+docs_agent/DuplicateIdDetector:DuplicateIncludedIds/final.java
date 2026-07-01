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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
 * Checks for duplicate ids within a layout and across layouts that are combined
 * via include tags.
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
    private final Map<String, List<String>> mFileToIncludes = new HashMap<>();

    /** Map from layout name to the file */
    private final Map<String, File> mFileMap = new HashMap<>();

    /** Map from layout name to id -> location mapping */
    private final Map<String, Map<String, Location>> mFileToIdLocations = new HashMap<>();

    /** Current file being analyzed */
    private String mCurrentFile;

    /** Ids defined in the current file */
    private Set<String> mCurrentIds;

    /** Includes in the current file */
    private List<String> mCurrentIncludes;

    /** Id locations in the current file */
    private Map<String, Location> mCurrentIdLocations;

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        mCurrentFile = getLayoutName(file);
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
        mCurrentIdLocations = new HashMap<>();
        mFileToIds.put(mCurrentFile, mCurrentIds);
        mFileToIncludes.put(mCurrentFile, mCurrentIncludes);
        mFileMap.put(mCurrentFile, file);
        mFileToIdLocations.put(mCurrentFile, mCurrentIdLocations);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (TAG_INCLUDE.equals(tag)) {
            // Record the include
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                mCurrentIncludes.add(includedLayout);
            }
        }

        // Record the id of this element
        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            if (id != null && !id.isEmpty()) {
                // Normalize the id (strip @+id/ or @id/ prefix)
                String normalizedId = id;
                if (normalizedId.startsWith("@+id/")) {
                    normalizedId = normalizedId.substring("@+id/".length());
                } else if (normalizedId.startsWith("@id/")) {
                    normalizedId = normalizedId.substring("@id/".length());
                }
                mCurrentIds.add(normalizedId);
                if (!mCurrentIdLocations.containsKey(normalizedId)) {
                    mCurrentIdLocations.put(normalizedId, context.getLocation(idAttr));
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now analyze the include graph for duplicate ids
        // For each layout that includes other layouts, traverse the include chain
        // and check for duplicate ids

        Set<String> allLayouts = new HashSet<>(mFileToIds.keySet());

        // Track reported issues to avoid duplicates
        Set<String> reported = new HashSet<>();

        for (String rootLayout : allLayouts) {
            List<String> includes = mFileToIncludes.get(rootLayout);
            if (includes == null || includes.isEmpty()) {
                continue;
            }
            // This layout includes others, check for duplicate ids in the chain
            checkIncludeChain(rootLayout, context, reported);
        }
    }

    private void checkIncludeChain(String rootLayout, Context context, Set<String> reported) {
        // BFS/DFS to collect all ids in the include chain and detect duplicates
        // We track which layout each id comes from
        Map<String, String> idToLayout = new HashMap<>();
        Map<String, List<String>> duplicateIdToLayouts = new HashMap<>();

        // Use a stack for DFS traversal
        Deque<String> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        stack.push(rootLayout);

        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            Set<String> ids = mFileToIds.get(current);
            if (ids != null) {
                for (String id : ids) {
                    if (idToLayout.containsKey(id)) {
                        // Duplicate found
                        List<String> layouts = duplicateIdToLayouts.computeIfAbsent(
                                id, k -> new ArrayList<>());
                        String firstLayout = idToLayout.get(id);
                        if (!layouts.contains(firstLayout)) {
                            layouts.add(firstLayout);
                        }
                        if (!layouts.contains(current)) {
                            layouts.add(current);
                        }
                    } else {
                        idToLayout.put(id, current);
                    }
                }
            }

            List<String> childIncludes = mFileToIncludes.get(current);
            if (childIncludes != null) {
                for (String child : childIncludes) {
                    if (!visited.contains(child)) {
                        stack.push(child);
                    }
                }
            }
        }

        // Report duplicates
        for (Map.Entry<String, List<String>> entry : duplicateIdToLayouts.entrySet()) {
            String id = entry.getKey();
            List<String> layouts = entry.getValue();

            // Create a unique key for this set of duplicates to avoid reporting the same
            // issue multiple times
            List<String> sortedLayouts = new ArrayList<>(layouts);
            Collections.sort(sortedLayouts);
            String key = id + ":" + sortedLayouts.toString();
            if (reported.contains(key)) {
                continue;
            }
            reported.add(key);

            // Build the message
            StringBuilder sb = new StringBuilder();
            sb.append("Duplicate id `@+id/").append(id)
              .append("` across layouts: ");
            for (int i = 0; i < sortedLayouts.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(sortedLayouts.get(i));
            }
            sb.append(" (included from ").append(rootLayout).append(")");

            // Find locations to report - use id-specific locations when available
            Location location = null;
            for (int i = sortedLayouts.size() - 1; i >= 0; i--) {
                String layout = sortedLayouts.get(i);
                Location loc = getIdLocation(layout, id);
                if (loc == null) {
                    loc = getFileLocation(context, layout);
                }
                if (loc != null) {
                    if (location == null) {
                        location = loc;
                    } else {
                        Location secondary = loc;
                        secondary.setSecondary(location);
                        location = secondary;
                    }
                }
            }

            if (location == null) {
                location = getFileLocation(context, rootLayout);
            }

            if (location != null) {
                context.report(CROSS_LAYOUT, location, sb.toString());
            }
        }
    }

    @Nullable
    private Location getIdLocation(String layout, String id) {
        Map<String, Location> idLocations = mFileToIdLocations.get(layout);
        if (idLocations != null) {
            return idLocations.get(id);
        }
        return null;
    }

    @Nullable
    private Location getFileLocation(Context context, String layout) {
        File file = mFileMap.get(layout);
        if (file != null) {
            return Location.create(file);
        }
        return null;
    }

    private static String getLayoutName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }
}