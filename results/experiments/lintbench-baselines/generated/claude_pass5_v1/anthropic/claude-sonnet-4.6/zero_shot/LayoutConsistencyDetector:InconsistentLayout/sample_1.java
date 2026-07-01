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
 * Checks for inconsistencies between different layout versions of the same layout.
 * Specifically, it checks that layouts defined in multiple resource folders contain
 * the same set of widgets (by android:id), to avoid potential NullPointerExceptions
 * when code calls findViewById() and the view doesn't exist in a particular configuration.
 */
public class LayoutConsistencyDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue INCONSISTENT_IDS = Issue.create(
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
     * Map from layout name to a map of folder -> list of ids found in that folder's version
     * of the layout.
     * Key: layout file name (e.g. "main.xml")
     * Value: Map from folder name (e.g. "layout", "layout-land") to list of view ids
     */
    private final Map<String, Map<String, List<String>>> mFileToFolderToIds = new HashMap<>();

    /**
     * Map from layout name + folder to the XmlContext (for location reporting).
     * Key: "layoutname/foldername"
     * Value: location of the layout file
     */
    private final Map<String, Location> mFileToLocation = new HashMap<>();

    /** Constructs a new {@link LayoutConsistencyDetector} */
    public LayoutConsistencyDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("id");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // We only care about android:id attributes
        if (!attribute.getLocalName().equals("id")) {
            return;
        }
        String namespace = attribute.getNamespaceURI();
        if (namespace == null || !namespace.equals("http://schemas.android.com/apk/res/android")) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        // Normalize the id: strip @+id/ or @id/ prefix
        String id = value;
        if (id.startsWith("@+id/")) {
            id = id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            id = id.substring("@id/".length());
        } else {
            // Not a normal id reference, skip
            return;
        }

        File file = context.file;
        String fileName = file.getName();
        File parentFolder = file.getParentFile();
        String folderName = parentFolder != null ? parentFolder.getName() : "layout";

        // Register this id for this layout file + folder combination
        Map<String, List<String>> folderToIds = mFileToFolderToIds.get(fileName);
        if (folderToIds == null) {
            folderToIds = new HashMap<>();
            mFileToFolderToIds.put(fileName, folderToIds);
        }

        List<String> ids = folderToIds.get(folderName);
        if (ids == null) {
            ids = new ArrayList<>();
            folderToIds.put(folderName, ids);
        }

        if (!ids.contains(id)) {
            ids.add(id);
        }

        // Store location for this file
        String locationKey = fileName + "/" + folderName;
        if (!mFileToLocation.containsKey(locationKey)) {
            mFileToLocation.put(locationKey, context.getLocation(attribute.getOwnerElement()));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Now compare the id sets across different folders for the same layout file
        for (Map.Entry<String, Map<String, List<String>>> entry : mFileToFolderToIds.entrySet()) {
            String fileName = entry.getKey();
            Map<String, List<String>> folderToIds = entry.getValue();

            // Only check layouts that appear in more than one folder
            if (folderToIds.size() <= 1) {
                continue;
            }

            // Compute the union of all ids across all folders
            Set<String> allIds = new HashSet<>();
            for (List<String> ids : folderToIds.values()) {
                allIds.addAll(ids);
            }

            // For each folder, find ids that are missing
            // We need to find the "canonical" or most common set to compare against
            // Strategy: find ids that appear in some folders but not all
            for (Map.Entry<String, List<String>> folderEntry : folderToIds.entrySet()) {
                String folderName = folderEntry.getKey();
                List<String> folderIds = folderEntry.getValue();
                Set<String> folderIdSet = new HashSet<>(folderIds);

                // Find ids in other folders that are missing from this folder
                Set<String> missingIds = new HashSet<>();
                for (Map.Entry<String, List<String>> otherFolderEntry : folderToIds.entrySet()) {
                    if (otherFolderEntry.getKey().equals(folderName)) {
                        continue;
                    }
                    for (String otherId : otherFolderEntry.getValue()) {
                        if (!folderIdSet.contains(otherId)) {
                            missingIds.add(otherId);
                        }
                    }
                }

                if (!missingIds.isEmpty()) {
                    String locationKey = fileName + "/" + folderName;
                    Location location = mFileToLocation.get(locationKey);

                    // Sort missing ids for deterministic output
                    List<String> sortedMissing = new ArrayList<>(missingIds);
                    Collections.sort(sortedMissing);

                    // Find which folders have the missing ids (for the message)
                    // Build a description of where these ids appear
                    StringBuilder sb = new StringBuilder();
                    sb.append("The id ");
                    if (sortedMissing.size() == 1) {
                        sb.append('`').append(sortedMissing.get(0)).append('`');
                    } else {
                        for (int i = 0; i < sortedMissing.size(); i++) {
                            if (i > 0) {
                                if (i == sortedMissing.size() - 1) {
                                    sb.append(" and ");
                                } else {
                                    sb.append(", ");
                                }
                            }
                            sb.append('`').append(sortedMissing.get(i)).append('`');
                        }
                    }
                    sb.append(" is missing from the `").append(folderName).append("` layout");

                    // Find which folders contain these missing ids
                    Set<String> foldersWithMissing = new HashSet<>();
                    for (String missingId : missingIds) {
                        for (Map.Entry<String, List<String>> otherFolderEntry : folderToIds.entrySet()) {
                            if (!otherFolderEntry.getKey().equals(folderName)
                                    && otherFolderEntry.getValue().contains(missingId)) {
                                foldersWithMissing.add(otherFolderEntry.getKey());
                            }
                        }
                    }

                    List<String> sortedFolders = new ArrayList<>(foldersWithMissing);
                    Collections.sort(sortedFolders);

                    sb.append(", but is in ");
                    for (int i = 0; i < sortedFolders.size(); i++) {
                        if (i > 0) {
                            if (i == sortedFolders.size() - 1) {
                                sb.append(" and ");
                            } else {
                                sb.append(", ");
                            }
                        }
                        sb.append('`').append(sortedFolders.get(i)).append('`');
                    }

                    if (location != null) {
                        context.report(INCONSISTENT_IDS, location, sb.toString());
                    } else {
                        // Try to get a file-level location
                        File layoutFile = findLayoutFile(context, fileName, folderName);
                        Location fileLoc = layoutFile != null
                                ? Location.create(layoutFile)
                                : Location.create(context.file);
                        context.report(INCONSISTENT_IDS, fileLoc, sb.toString());
                    }
                }
            }
        }
    }

    /**
     * Attempts to find the layout file for the given file name and folder name.
     */
    @Nullable
    private File findLayoutFile(@NonNull Context context, @NonNull String fileName,
            @NonNull String folderName) {
        File resourceDir = context.file.getParentFile();
        if (resourceDir == null) {
            return null;
        }
        // Walk up to find the res directory
        File resDir = resourceDir.getParentFile();
        if (resDir == null) {
            return null;
        }
        File folder = new File(resDir, folderName);
        if (folder.exists()) {
            File file = new File(folder, fileName);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }
}