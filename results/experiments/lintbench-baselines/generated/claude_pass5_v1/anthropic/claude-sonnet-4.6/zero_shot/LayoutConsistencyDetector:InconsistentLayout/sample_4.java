/*
 * Copyright (C) 2013 The Android Open Source Project
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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

/**
 * Checks for inconsistencies between multiple layout versions of the same layout.
 */
public class LayoutConsistencyDetector extends LayoutDetector {

    /** Map from layout resource name to a map of folder to list of ids in that layout */
    private final Map<String, Map<String, List<String>>> mFileToIds =
            new HashMap<String, Map<String, List<String>>>();

    /** Map from layout resource name to a map of id to list of locations for that id */
    private final Map<String, Map<String, List<Location>>> mIdToLocations =
            new HashMap<String, Map<String, List<Location>>>();

    public static final Issue ISSUE = Issue.create(
            "InconsistentLayout",
            "Inconsistent Layouts",
            "This check ensures that a layout resource which is defined in multiple " +
            "resource folders, specifies the same set of widgets.\n" +
            "\n" +
            "This finds cases where you have accidentally forgotten to add " +
            "a widget to all variations of the layout, which could result " +
            "in a runtime crash for some resource configurations when a " +
            "`findViewById()` fails.\n" +
            "\n" +
            "There **are** cases where this is intentional. For example, you " +
            "may have a dedicated large tablet layout which adds some extra " +
            "widgets that are not present in the phone version of the layout. " +
            "As long as the code accessing the layout resource is careful to " +
            "handle this properly, it is valid. In that case, you can suppress " +
            "this lint check for the given extra or missing views, or the whole " +
            "layout",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    LayoutConsistencyDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    /** Constructs a new {@link LayoutConsistencyDetector} */
    public LayoutConsistencyDetector() {
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
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We only care about elements that have an android:id attribute
        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue();
        if (id.isEmpty()) {
            return;
        }

        // Normalize the id: strip @+id/ or @id/ prefix
        String normalizedId = normalizeId(id);

        // Get the layout file name (without extension)
        File file = context.file;
        String layoutName = getLayoutName(file);

        // Get the folder name (e.g. "layout", "layout-land", "layout-large")
        String folderName = file.getParentFile().getName();

        // Record this id for this layout + folder combination
        Map<String, List<String>> folderToIds = mFileToIds.get(layoutName);
        if (folderToIds == null) {
            folderToIds = new HashMap<String, List<String>>();
            mFileToIds.put(layoutName, folderToIds);
        }

        List<String> ids = folderToIds.get(folderName);
        if (ids == null) {
            ids = new ArrayList<String>();
            folderToIds.put(folderName, ids);
        }

        if (!ids.contains(normalizedId)) {
            ids.add(normalizedId);
        }

        // Record the location for this id
        Map<String, List<Location>> idToLocations = mIdToLocations.get(layoutName);
        if (idToLocations == null) {
            idToLocations = new HashMap<String, List<Location>>();
            mIdToLocations.put(layoutName, idToLocations);
        }

        List<Location> locations = idToLocations.get(normalizedId);
        if (locations == null) {
            locations = new ArrayList<Location>();
            idToLocations.put(normalizedId, locations);
        }

        Location location = context.getLocation(idAttr);
        // Store folder info in the location message
        location.setMessage(folderName);
        locations.add(location);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // Now analyze the collected data for inconsistencies
        for (Map.Entry<String, Map<String, List<String>>> entry : mFileToIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, List<String>> folderToIds = entry.getValue();

            // Only check layouts that appear in multiple folders
            if (folderToIds.size() < 2) {
                continue;
            }

            checkLayoutConsistency(context, layoutName, folderToIds);
        }
    }

    private void checkLayoutConsistency(
            @NonNull Context context,
            @NonNull String layoutName,
            @NonNull Map<String, List<String>> folderToIds) {

        // Collect all unique ids across all folders
        Set<String> allIds = new HashSet<String>();
        for (List<String> ids : folderToIds.values()) {
            allIds.addAll(ids);
        }

        // For each folder, check if it's missing any ids that appear in other folders
        List<String> folders = new ArrayList<String>(folderToIds.keySet());
        Collections.sort(folders);

        // Build a map of id -> set of folders that contain it
        Map<String, Set<String>> idToFolders = new HashMap<String, Set<String>>();
        for (Map.Entry<String, List<String>> folderEntry : folderToIds.entrySet()) {
            String folder = folderEntry.getKey();
            for (String id : folderEntry.getValue()) {
                Set<String> foldersWithId = idToFolders.get(id);
                if (foldersWithId == null) {
                    foldersWithId = new HashSet<String>();
                    idToFolders.put(id, foldersWithId);
                }
                foldersWithId.add(folder);
            }
        }

        // Find ids that are not present in all folders
        Map<String, List<Location>> idToLocations = mIdToLocations.get(layoutName);

        for (Map.Entry<String, Set<String>> idEntry : idToFolders.entrySet()) {
            String id = idEntry.getKey();
            Set<String> foldersWithId = idEntry.getValue();

            if (foldersWithId.size() == folderToIds.size()) {
                // This id is in all folders, no problem
                continue;
            }

            // This id is missing from some folders
            Set<String> missingFromFolders = new HashSet<String>(folderToIds.keySet());
            missingFromFolders.removeAll(foldersWithId);

            // Build the error message
            List<String> presentFolderList = new ArrayList<String>(foldersWithId);
            Collections.sort(presentFolderList);
            List<String> missingFolderList = new ArrayList<String>(missingFromFolders);
            Collections.sort(missingFolderList);

            String message = String.format(
                    "The id \"%1$s\" in layout \"%2$s\" is missing from the following layout " +
                    "configurations: %3$s",
                    id,
                    layoutName,
                    formatFolderList(missingFolderList));

            // Find the location(s) for this id to attach the error
            Location location = null;
            if (idToLocations != null) {
                List<Location> locations = idToLocations.get(id);
                if (locations != null && !locations.isEmpty()) {
                    // Use the first location as the primary, chain others as secondaries
                    location = locations.get(0);
                    Location prev = null;
                    for (int i = locations.size() - 1; i >= 1; i--) {
                        Location secondary = locations.get(i);
                        secondary.setMessage("Also defined here");
                        if (prev != null) {
                            secondary.setSecondary(prev);
                        }
                        prev = secondary;
                    }
                    if (prev != null) {
                        location.setSecondary(prev);
                    }
                }
            }

            if (location != null) {
                context.report(ISSUE, location, message);
            }
        }
    }

    /**
     * Returns the layout name (filename without extension).
     */
    @NonNull
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            return name.substring(0, dot);
        }
        return name;
    }

    /**
     * Normalizes an id value by stripping the @+id/ or @id/ prefix.
     */
    @NonNull
    private static String normalizeId(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring(5);
        } else if (id.startsWith("@id/")) {
            return id.substring(4);
        }
        return id;
    }

    /**
     * Formats a list of folder names for display in an error message.
     */
    @NonNull
    private static String formatFolderList(@NonNull List<String> folders) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < folders.size(); i++) {
            if (i > 0) {
                if (i == folders.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(folders.get(i));
        }
        return sb.toString();
    }
}