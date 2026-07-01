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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks for inconsistencies between multiple layout versions of the same layout.
 * Specifically, it ensures that all variations of a layout define the same set of
 * widgets (identified by their android:id attributes).
 */
public class LayoutConsistencyDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
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

    /**
     * Map from layout name to a map of folder name -> list of ids found in that folder's version.
     * Key: layout file base name (e.g. "activity_main")
     * Value: map of folder qualifier (e.g. "layout", "layout-land") -> set of view ids
     */
    private final Map<String, Map<String, Set<String>>> mLayoutToFolderIds =
            new HashMap<>();

    /**
     * Map from layout name + id to the Location of that id declaration.
     * Key: "layoutName:folderId:viewId"
     * Value: Location for that view
     */
    private final Map<String, Location> mLocations = new HashMap<>();

    /** Constructs a new {@link LayoutConsistencyDetector} */
    public LayoutConsistencyDetector() {
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
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We only care about elements that have an android:id attribute
        Attr idAttr = element.getAttributeNodeNS(
                "http://schemas.android.com/apk/res/android", "id");
        if (idAttr == null) {
            return;
        }

        String idValue = idAttr.getValue();
        if (idValue == null || idValue.isEmpty()) {
            return;
        }

        // Normalize the id: strip @+id/ or @id/ prefix
        String id = normalizeId(idValue);
        if (id == null || id.isEmpty()) {
            return;
        }

        // Get the layout file name (without extension)
        File file = context.file;
        String layoutName = getLayoutName(file);

        // Get the folder name (the parent directory name, e.g. "layout" or "layout-land")
        String folderName = file.getParentFile().getName();

        // Record this id for this layout + folder combination
        Map<String, Set<String>> folderToIds = mLayoutToFolderIds.get(layoutName);
        if (folderToIds == null) {
            folderToIds = new HashMap<>();
            mLayoutToFolderIds.put(layoutName, folderToIds);
        }

        Set<String> ids = folderToIds.get(folderName);
        if (ids == null) {
            ids = new HashSet<>();
            folderToIds.put(folderName, ids);
        }
        ids.add(id);

        // Store location for reporting
        String locationKey = layoutName + ":" + folderName + ":" + id;
        if (!mLocations.containsKey(locationKey)) {
            mLocations.put(locationKey, context.getLocation(idAttr));
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // Now compare the id sets across all folders for each layout
        for (Map.Entry<String, Map<String, Set<String>>> entry : mLayoutToFolderIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Set<String>> folderToIds = entry.getValue();

            // Only relevant if the layout appears in more than one folder
            if (folderToIds.size() <= 1) {
                continue;
            }

            // Compute the union of all ids across all folders
            Set<String> allIds = new HashSet<>();
            for (Set<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            // For each folder, find ids that are missing compared to the union
            List<String> folders = new ArrayList<>(folderToIds.keySet());
            Collections.sort(folders);

            for (String folder : folders) {
                Set<String> idsInFolder = folderToIds.get(folder);

                // Find ids present in other folders but not in this one
                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(idsInFolder);

                if (!missingIds.isEmpty()) {
                    // Find which folders DO have these missing ids
                    List<String> sortedMissingIds = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissingIds);

                    for (String missingId : sortedMissingIds) {
                        // Find a location in another folder that has this id, for secondary location
                        Location primaryLocation = null;
                        Location secondaryLocation = null;

                        // Try to find a location for the missing id in another folder
                        for (String otherFolder : folders) {
                            if (!otherFolder.equals(folder)) {
                                Set<String> otherIds = folderToIds.get(otherFolder);
                                if (otherIds != null && otherIds.contains(missingId)) {
                                    String locKey = layoutName + ":" + otherFolder + ":" + missingId;
                                    Location loc = mLocations.get(locKey);
                                    if (loc != null) {
                                        secondaryLocation = loc;
                                        break;
                                    }
                                }
                            }
                        }

                        // Build a message describing the inconsistency
                        StringBuilder sb = new StringBuilder();
                        sb.append("The id `").append(missingId).append("` in layout `")
                                .append(layoutName).append("` is missing from the `")
                                .append(folder).append("` layout");

                        // List which folders have it
                        List<String> foldersWithId = new ArrayList<>();
                        for (String f : folders) {
                            if (!f.equals(folder)) {
                                Set<String> fIds = folderToIds.get(f);
                                if (fIds != null && fIds.contains(missingId)) {
                                    foldersWithId.add(f);
                                }
                            }
                        }
                        if (!foldersWithId.isEmpty()) {
                            sb.append(" (present in: ");
                            for (int i = 0; i < foldersWithId.size(); i++) {
                                if (i > 0) {
                                    sb.append(", ");
                                }
                                sb.append(foldersWithId.get(i));
                            }
                            sb.append(")");
                        }

                        String message = sb.toString();

                        // We need a location; use the secondary location (where the id IS defined)
                        // as the primary report location, and note the folder it's missing from
                        if (secondaryLocation != null) {
                            if (primaryLocation == null) {
                                primaryLocation = secondaryLocation;
                            }
                            context.report(ISSUE, primaryLocation, message);
                        } else {
                            // Fallback: report without specific location
                            context.report(ISSUE, Location.create(context.getProject().getDir()),
                                    message);
                        }
                    }
                }
            }
        }
    }

    /**
     * Normalizes an Android id value by stripping the @+id/ or @id/ prefix.
     *
     * @param id the raw id value (e.g. "@+id/my_button" or "@id/my_button")
     * @return the normalized id (e.g. "my_button"), or null if not a valid id
     */
    @Nullable
    private static String normalizeId(@NonNull String id) {
        if (id.startsWith("@+id/")) {
            return id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            return id.substring("@id/".length());
        }
        return id;
    }

    /**
     * Gets the layout name from a file (strips the .xml extension).
     *
     * @param file the layout file
     * @return the layout name without extension
     */
    @NonNull
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) {
            return name.substring(0, dotIndex);
        }
        return name;
    }
}