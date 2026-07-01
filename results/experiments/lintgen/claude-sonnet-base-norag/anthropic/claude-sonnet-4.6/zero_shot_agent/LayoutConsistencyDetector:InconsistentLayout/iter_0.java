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
import com.android.tools.lint.detector.api.LintFix;
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
 * Specifically, it checks that all layout variations define the same set of view IDs.
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
     * Map from layout base name to a map of (folder -> list of view ids).
     * e.g. "activity_main" -> { "layout" -> ["@+id/button1", "@+id/text1"], "layout-land" -> [...] }
     */
    private final Map<String, Map<String, List<String>>> mLayoutToFolderIds = new HashMap<>();

    /**
     * Map from layout base name + folder to the XmlContext location handle for reporting.
     * Key: "layoutName:folderName"
     */
    private final Map<String, Location.Handle> mLocationHandles = new HashMap<>();

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
        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        // Get the layout file name (without extension)
        File file = context.file;
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String layoutName = dotIndex != -1 ? fileName.substring(0, dotIndex) : fileName;

        // Get the folder name (e.g., "layout", "layout-land", "layout-xlarge")
        File folder = file.getParentFile();
        String folderName = folder != null ? folder.getName() : "layout";

        // Store the id for this layout/folder combination
        Map<String, List<String>> folderToIds = mLayoutToFolderIds.get(layoutName);
        if (folderToIds == null) {
            folderToIds = new HashMap<>();
            mLayoutToFolderIds.put(layoutName, folderToIds);
        }

        List<String> ids = folderToIds.get(folderName);
        if (ids == null) {
            ids = new ArrayList<>();
            folderToIds.put(folderName, ids);
        }

        if (!ids.contains(id)) {
            ids.add(id);
        }

        // Store a location handle for the root element of this layout/folder
        String key = layoutName + ":" + folderName;
        if (!mLocationHandles.containsKey(key)) {
            // Store a handle to the document root for location reporting
            mLocationHandles.put(key, context.createLocationHandle(element));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now compare all layout variations and report inconsistencies
        for (Map.Entry<String, Map<String, List<String>>> entry : mLayoutToFolderIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, List<String>> folderToIds = entry.getValue();

            // Only check if there are multiple folder variations
            if (folderToIds.size() < 2) {
                continue;
            }

            // Compute the union of all IDs across all folders
            Set<String> allIds = new HashSet<>();
            for (List<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            // For each folder, find missing IDs
            for (Map.Entry<String, List<String>> folderEntry : folderToIds.entrySet()) {
                String folderName = folderEntry.getKey();
                List<String> ids = folderEntry.getValue();
                Set<String> idSet = new HashSet<>(ids);

                // Find IDs present in other folders but missing from this one
                Set<String> missingIds = new HashSet<>(allIds);
                missingIds.removeAll(idSet);

                if (!missingIds.isEmpty()) {
                    // Find which folders have the missing IDs
                    List<String> sortedMissing = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissing);

                    StringBuilder message = new StringBuilder();
                    message.append("The id ");

                    if (sortedMissing.size() == 1) {
                        message.append("`").append(sortedMissing.get(0)).append("`");
                        message.append(" in layout `").append(folderName)
                               .append("/").append(layoutName).append("`");
                        message.append(" is missing from the following layout configurations: ");
                    } else {
                        message.append("s ");
                        for (int i = 0; i < sortedMissing.size(); i++) {
                            if (i > 0) {
                                message.append(", ");
                            }
                            message.append("`").append(sortedMissing.get(i)).append("`");
                        }
                        message.append(" in layout `").append(folderName)
                               .append("/").append(layoutName).append("`");
                        message.append(" are missing from the following layout configurations: ");
                    }

                    // Find which folders are missing these IDs
                    List<String> foldersWithMissing = new ArrayList<>();
                    for (Map.Entry<String, List<String>> otherFolderEntry : folderToIds.entrySet()) {
                        String otherFolder = otherFolderEntry.getKey();
                        if (otherFolder.equals(folderName)) {
                            continue;
                        }
                        List<String> otherIds = otherFolderEntry.getValue();
                        // Check if this other folder is missing any of the IDs
                        boolean missingAny = false;
                        for (String missingId : sortedMissing) {
                            if (!otherIds.contains(missingId)) {
                                missingAny = true;
                                break;
                            }
                        }
                        if (missingAny) {
                            foldersWithMissing.add(otherFolder);
                        }
                    }

                    // Build the list of folders that have the missing IDs (i.e., where they exist)
                    List<String> foldersWithIds = new ArrayList<>();
                    for (Map.Entry<String, List<String>> otherFolderEntry : folderToIds.entrySet()) {
                        String otherFolder = otherFolderEntry.getKey();
                        if (otherFolder.equals(folderName)) {
                            continue;
                        }
                        List<String> otherIds = otherFolderEntry.getValue();
                        boolean hasAll = true;
                        for (String missingId : sortedMissing) {
                            if (!otherIds.contains(missingId)) {
                                hasAll = false;
                                break;
                            }
                        }
                        if (hasAll) {
                            foldersWithIds.add(otherFolder);
                        }
                    }

                    Collections.sort(foldersWithMissing);

                    for (int i = 0; i < foldersWithMissing.size(); i++) {
                        if (i > 0) {
                            message.append(", ");
                        }
                        message.append(foldersWithMissing.get(i));
                    }

                    // Get the location for this layout/folder
                    String key = layoutName + ":" + folderName;
                    Location.Handle handle = mLocationHandles.get(key);
                    Location location = handle != null ? handle.resolve() : Location.NONE;

                    // Add secondary locations for the other folders
                    Location secondary = null;
                    List<String> otherFolders = new ArrayList<>(folderToIds.keySet());
                    Collections.sort(otherFolders);
                    for (String otherFolder : otherFolders) {
                        if (otherFolder.equals(folderName)) {
                            continue;
                        }
                        String otherKey = layoutName + ":" + otherFolder;
                        Location.Handle otherHandle = mLocationHandles.get(otherKey);
                        if (otherHandle != null) {
                            Location otherLocation = otherHandle.resolve();
                            otherLocation.setMessage("Defined here");
                            if (secondary == null) {
                                secondary = otherLocation;
                            } else {
                                // Chain locations
                                Location last = secondary;
                                while (last.getSecondary() != null) {
                                    last = last.getSecondary();
                                }
                                last.setSecondary(otherLocation);
                            }
                        }
                    }

                    if (secondary != null) {
                        location.setSecondary(secondary);
                    }

                    context.report(ISSUE, location, message.toString());
                }
            }
        }
    }
}