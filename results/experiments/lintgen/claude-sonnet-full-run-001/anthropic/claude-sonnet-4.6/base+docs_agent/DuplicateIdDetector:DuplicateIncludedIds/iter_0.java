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

import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.TOOLS_URI;

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

    /** Map from layout name to the file location */
    private final Map<String, Location> mFileToLocation = new HashMap<>();

    /** Current file being analyzed */
    private String mCurrentFile;

    /** Ids defined in the current file */
    private Set<String> mCurrentIds;

    /** Includes in the current file */
    private List<String> mCurrentIncludes;

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_INCLUDE, "*");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        mCurrentFile = getLayoutName(file);
        mCurrentIds = new HashSet<>();
        mCurrentIncludes = new ArrayList<>();
        mFileToIds.put(mCurrentFile, mCurrentIds);
        mFileToIncludes.put(mCurrentFile, mCurrentIncludes);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        // Record the location of the file (use the root element location)
        if (element.getParentNode() == element.getOwnerDocument().getDocumentElement()
                || element.getParentNode() == element.getOwnerDocument()) {
            if (!mFileToLocation.containsKey(mCurrentFile)) {
                mFileToLocation.put(mCurrentFile, context.getLocation(element));
            }
        }

        if (TAG_INCLUDE.equals(tag)) {
            // Record the include
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                mCurrentIncludes.add(includedLayout);
            }
        }

        // Record the id of this element
        Attr idAttr = element.getAttributeNodeNS(
                "http://schemas.android.com/apk/res/android", ATTR_ID);
        if (idAttr != null) {
            String id = idAttr.getValue();
            if (id != null && !id.isEmpty()) {
                // Normalize the id (strip @+id/ or @id/ prefix)
                if (id.startsWith("@+id/")) {
                    id = id.substring("@+id/".length());
                } else if (id.startsWith("@id/")) {
                    id = id.substring("@id/".length());
                }
                mCurrentIds.add(id);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now analyze the include graph for duplicate ids
        // For each layout, do a DFS/BFS through its include chain and check for duplicate ids

        // We need to find all layouts that are "roots" (not included by anyone) or just
        // check all layouts as potential roots
        Set<String> allLayouts = mFileToIds.keySet();

        // For each layout, traverse its include tree and find duplicate ids
        // We report issues per include chain
        Set<String> reported = new HashSet<>();

        for (String rootLayout : allLayouts) {
            checkIncludeChain(rootLayout, reported, context);
        }
    }

    private void checkIncludeChain(String rootLayout, Set<String> reported, Context context) {
        List<String> includes = mFileToIncludes.get(rootLayout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        // BFS/DFS to collect all ids in the include chain and detect duplicates
        // We track which layout each id comes from
        Map<String, String> idToLayout = new HashMap<>();
        Set<String> duplicateIds = new HashSet<>();
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
                        duplicateIds.add(id);
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

            // Find a location to report
            Location location = null;
            for (String layout : sortedLayouts) {
                Location loc = mFileToLocation.get(layout);
                if (loc != null) {
                    if (location == null) {
                        location = loc;
                    } else {
                        location = loc.withSecondary(location,
                                "Also defined here");
                    }
                }
            }

            if (location == null) {
                location = mFileToLocation.get(rootLayout);
            }

            if (location != null) {
                context.report(CROSS_LAYOUT, location, sb.toString());
            }
        }
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