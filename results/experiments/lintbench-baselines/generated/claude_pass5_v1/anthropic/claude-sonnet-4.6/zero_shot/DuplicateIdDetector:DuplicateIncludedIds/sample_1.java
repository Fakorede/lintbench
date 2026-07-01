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
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.TAG_INCLUDE;
import static com.android.SdkConstants.TOOLS_URI;

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
            "Within a layout, id's should be unique since otherwise `findViewById()` can " +
            "return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /**
     * Map from layout resource name to the set of ids defined in that layout
     * (including ids from transitively included layouts).
     */
    private Map<String, Set<String>> mFileToIds;

    /**
     * Map from layout resource name to the list of layouts it includes.
     */
    private Map<String, List<String>> mIncludes;

    /** Set of layouts that have already been processed for cross-layout duplicates */
    private Set<String> mProcessed;

    /** Constructs a new {@link DuplicateIdDetector} */
    public DuplicateIdDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFileToIds = new HashMap<>();
        mIncludes = new HashMap<>();
        mProcessed = new HashSet<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check for duplicate ids within the same layout file
        String tagName = element.getTagName();

        if (tagName.equals(TAG_INCLUDE)) {
            // Record include relationships
            String layout = element.getAttribute(ATTR_LAYOUT);
            if (layout != null && layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
                String includedLayout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
                String currentLayout = getLayoutName(context);
                List<String> includes = mIncludes.get(currentLayout);
                if (includes == null) {
                    includes = new ArrayList<>();
                    mIncludes.put(currentLayout, includes);
                }
                includes.add(includedLayout);
            }
        }

        // Check for id attribute
        Attr idAttr = element.getAttributeNode(ATTR_ID);
        if (idAttr == null) {
            // Also check tools:id
            idAttr = element.getAttributeNodeNS(TOOLS_URI, ATTR_ID);
        }

        if (idAttr != null) {
            String id = idAttr.getValue();
            if (id != null && !id.isEmpty()) {
                String currentLayout = getLayoutName(context);

                // Track all ids per file for cross-layout duplicate checking
                Set<String> ids = mFileToIds.get(currentLayout);
                if (ids == null) {
                    ids = new HashSet<>();
                    mFileToIds.put(currentLayout, ids);
                }

                // Check within-layout duplicates
                if (!ids.add(id)) {
                    String message = String.format(
                            "Duplicate id `%1$s`, already defined earlier in this layout",
                            id);
                    context.report(WITHIN_LAYOUT, idAttr, context.getLocation(idAttr), message);
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes.isEmpty()) {
            return;
        }

        // Now check for cross-layout duplicate ids
        // For each layout that includes other layouts, check if there are duplicate ids
        // in the combined set of ids.
        for (String layout : mIncludes.keySet()) {
            checkLayoutForDuplicates(context, layout);
        }
    }

    /**
     * Returns the layout resource name for the given context (without the layout/ prefix).
     */
    @NonNull
    private static String getLayoutName(@NonNull XmlContext context) {
        String name = context.file.getName();
        // Strip .xml extension
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name;
    }

    /**
     * Checks a layout (and all layouts it includes) for duplicate ids.
     */
    private void checkLayoutForDuplicates(@NonNull Context context, @NonNull String layout) {
        if (mProcessed.contains(layout)) {
            return;
        }
        mProcessed.add(layout);

        List<String> includes = mIncludes.get(layout);
        if (includes == null || includes.isEmpty()) {
            return;
        }

        // Collect all ids reachable from this layout via includes
        // Map from id to the layout where it was first seen
        Map<String, String> idToLayout = new HashMap<>();
        // Map from id to list of layouts where it appears (for reporting)
        Map<String, List<String>> duplicates = new HashMap<>();

        // Add ids from the root layout
        Set<String> rootIds = mFileToIds.get(layout);
        if (rootIds != null) {
            for (String id : rootIds) {
                idToLayout.put(id, layout);
            }
        }

        // BFS/DFS through included layouts
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        visited.add(layout);
        queue.add(layout);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> currentIncludes = mIncludes.get(current);
            if (currentIncludes == null) {
                continue;
            }
            for (String included : currentIncludes) {
                if (visited.contains(included)) {
                    continue;
                }
                visited.add(included);
                queue.add(included);

                Set<String> includedIds = mFileToIds.get(included);
                if (includedIds == null) {
                    continue;
                }
                for (String id : includedIds) {
                    if (idToLayout.containsKey(id)) {
                        // Duplicate found!
                        List<String> dups = duplicates.get(id);
                        if (dups == null) {
                            dups = new ArrayList<>();
                            dups.add(idToLayout.get(id));
                            duplicates.put(id, dups);
                        }
                        if (!dups.contains(included)) {
                            dups.add(included);
                        }
                    } else {
                        idToLayout.put(id, included);
                    }
                }
            }
        }

        // Report duplicates
        for (Map.Entry<String, List<String>> entry : duplicates.entrySet()) {
            String id = entry.getKey();
            List<String> layouts = entry.getValue();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                    "Duplicate id `%1$s` in layout `%2$s` included from `%3$s`",
                    id, layouts.get(1), layout));
            if (layouts.size() > 2) {
                sb.append(" (also defined in ");
                for (int i = 2; i < layouts.size(); i++) {
                    if (i > 2) {
                        sb.append(", ");
                    }
                    sb.append(layouts.get(i));
                }
                sb.append(")");
            }
            sb.append(": `").append(id).append("` defined in both `")
              .append(layouts.get(0)).append("` and `").append(layouts.get(1)).append("`");

            context.report(CROSS_LAYOUT, Location.create(context.file), sb.toString());
        }
    }
}