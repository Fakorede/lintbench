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

    /** Map from layout name to a map from id to the file defining it */
    private final Map<String, Map<String, Location>> mFileIds =
            new HashMap<String, Map<String, Location>>();

    /** Map from layout name to a list of layout files defining it */
    private final Map<String, List<File>> mLayouts =
            new HashMap<String, List<File>>();

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
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) {
            return;
        }

        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        String layoutName = getLayoutName(context.file);
        Map<String, Location> idMap = mFileIds.get(layoutName);
        if (idMap == null) {
            idMap = new HashMap<String, Location>();
            mFileIds.put(layoutName, idMap);
        }

        Location location = context.getLocation(idAttr);
        idMap.put(id, location);

        List<File> files = mLayouts.get(layoutName);
        if (files == null) {
            files = new ArrayList<File>();
            mLayouts.put(layoutName, files);
        }
        if (!files.contains(context.file)) {
            files.add(context.file);
        }
    }

    /**
     * Returns the layout name (without extension) for a given layout file.
     */
    private static String getLayoutName(File file) {
        String name = file.getName();
        int dotIndex = name.indexOf('.');
        if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // For each layout that appears in multiple folders, check that the set of ids is the same.
        // We need to re-collect per-file id sets.
        // mFileIds currently maps layoutName -> (id -> location), but we need per-file info.
        // Let's rebuild using a different structure.
        // Actually we need per-file id collection. Let me use mLayoutFileIds instead.
        // Since we already collected all ids into a flat map per layout name, we need per-file ids.
        // We need to restructure. Let's use the data we have in mLayoutFileIds.
        checkConsistency(context);
    }

    // We need per-file id sets. Let's add a field for that.
    // Map from layout name -> (file -> set of ids)
    private final Map<String, Map<File, Set<String>>> mLayoutFileIds =
            new HashMap<String, Map<File, Set<String>>>();

    // Map from layout name -> (file -> (id -> location))
    private final Map<String, Map<File, Map<String, Location>>> mLayoutFileLocations =
            new HashMap<String, Map<File, Map<String, Location>>>();

    @Override
    public void visitElement2(@NonNull XmlContext context, @NonNull Element element) {
        // This method is intentionally left empty; real work is in visitElement above.
        // We override visitElement to do the real collection using mLayoutFileIds.
    }

    // We need to override visitElement to collect per-file data.
    // Let's restructure: override visitElement only once.

    private void checkConsistency(@NonNull Context context) {
        // Use mLayoutFileIds which is populated in the real visitElement implementation below.
        for (Map.Entry<String, Map<File, Set<String>>> entry : mLayoutFileIds.entrySet()) {
            String layoutName = entry.getKey();
            Map<File, Set<String>> fileToIds = entry.getValue();

            if (fileToIds.size() < 2) {
                // Only defined in one folder, no inconsistency possible.
                continue;
            }

            // Compute the union of all ids across all files.
            Set<String> allIds = new HashSet<String>();
            for (Set<String> ids : fileToIds.values()) {
                allIds.addAll(ids);
            }

            // For each file, find ids that are missing.
            for (Map.Entry<File, Set<String>> fileEntry : fileToIds.entrySet()) {
                File file = fileEntry.getKey();
                Set<String> ids = fileEntry.getValue();

                Set<String> missingIds = new HashSet<String>(allIds);
                missingIds.removeAll(ids);

                if (!missingIds.isEmpty()) {
                    // Report missing ids for this file.
                    List<String> sortedMissing = new ArrayList<String>(missingIds);
                    Collections.sort(sortedMissing);

                    // Find a location for this file - use file-level location.
                    Location location = Location.create(file);

                    // Find secondary locations: where the missing ids ARE defined.
                    Location secondary = null;
                    for (String missingId : sortedMissing) {
                        for (Map.Entry<File, Map<String, Location>> locEntry :
                                mLayoutFileLocations.get(layoutName).entrySet()) {
                            if (locEntry.getKey().equals(file)) {
                                continue;
                            }
                            Map<String, Location> locMap = locEntry.getValue();
                            if (locMap.containsKey(missingId)) {
                                Location loc = locMap.get(missingId);
                                if (loc != null) {
                                    loc.setMessage("Defined here");
                                    loc.setSecondary(secondary);
                                    secondary = loc;
                                }
                            }
                        }
                    }

                    if (secondary != null) {
                        location.setSecondary(secondary);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Layout `").append(layoutName)
                            .append("` is missing the following IDs from other layout configurations: ");
                    for (int i = 0; i < sortedMissing.size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append("`").append(sortedMissing.get(i)).append("`");
                    }

                    context.report(INCONSISTENT_IDS, location, sb.toString());
                }
            }
        }
    }

    // ---- Real implementation ----

    // We need a clean single visitElement. The class has TWO visitElement methods which is wrong.
    // Let me rewrite the whole class properly below.
}

// The above has structural issues. Let me write the correct, clean version:

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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

/**
 * Checks for inconsistencies between multiple layout versions of the same layout.
 */
public class LayoutConsistencyDetector extends LayoutDetector {

    /**
     * Map from layout base name to a map from file to the set of ids defined in that file.
     */
    private final Map<String, Map<File, Set<String>>> mLayoutToFileIds =
            new HashMap<String, Map<File, Set<String>>>();

    /**
     * Map from layout base name to a map from file to a map from id to its location.
     */
    private final Map<String, Map<File, Map<String, Location>>> mLayoutToFileLocations =
            new HashMap<String, Map<File, Map<String, Location>>>();

    /** The main issue reported by this detector. */
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

    /** Constructs a new {@link LayoutConsistencyDetector}. */
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
        Attr idAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
        if (idAttr == null) {
            return;
        }

        String id = idAttr.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        File file = context.file;
        String layoutName = getLayoutName(file);

        // Collect id -> set of ids per file
        Map<File, Set<String>> fileToIds = mLayoutToFileIds.get(layoutName);
        if (fileToIds == null) {
            fileToIds = new HashMap<File, Set<String>>();
            mLayoutToFileIds.put(layoutName, fileToIds);
        }
        Set<String> ids = fileToIds.get(file);
        if (ids == null) {
            ids = new HashSet<String>();
            fileToIds.put(file, ids);
        }
        ids.add(id);

        // Collect id -> location per file
        Map<File, Map<String, Location>> fileToLocations = mLayoutToFileLocations.get(layoutName);
        if (fileToLocations == null) {
            fileToLocations = new HashMap<File, Map<String, Location>>();
            mLayoutToFileLocations.put(layoutName, fileToLocations);
        }
        Map<String, Location> locationMap = fileToLocations.get(file);
        if (locationMap == null) {
            locationMap = new HashMap<String, Location>();
            fileToLocations.put(file, locationMap);
        }
        locationMap.put(id, context.getLocation(idAttr));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Map<File, Set<String>>> layoutEntry : mLayoutToFileIds.entrySet()) {
            String layoutName = layoutEntry.getKey();
            Map<File, Set<String>> fileToIds = layoutEntry.getValue();

            if (fileToIds.size() < 2) {
                // Only defined in one configuration folder; no inconsistency possible.
                continue;
            }

            // Compute the union of all ids across all layout variations.
            Set<String> allIds = new HashSet<String>();
            for (Set<String> ids : fileToIds.values()) {
                allIds.addAll(ids);
            }

            Map<File, Map<String, Location>> fileToLocations =
                    mLayoutToFileLocations.get(layoutName);

            // For each file, report ids that are missing compared to the union.
            for (Map.Entry<File, Set<String>> fileEntry : fileToIds.entrySet()) {
                File file = fileEntry.getKey();
                Set<String> ids = fileEntry.getValue();

                Set<String> missingIds = new HashSet<String>(allIds);
                missingIds.removeAll(ids);

                if (missingIds.isEmpty()) {
                    continue;
                }

                List<String> sortedMissing = new ArrayList<String>(missingIds);
                Collections.sort(sortedMissing);

                // Build secondary locations pointing to where each missing id is defined.
                Location secondaryChain = null;
                for (String missingId : sortedMissing) {
                    if (fileToLocations != null) {
                        for (Map.Entry<File, Map<String, Location>> locEntry :
                                fileToLocations.entrySet()) {
                            if (locEntry.getKey().equals(file)) {
                                continue;
                            }
                            Map<String, Location> locMap = locEntry.getValue();
                            Location loc = locMap.get(missingId);
                            if (loc != null) {
                                Location copy = Location.create(
                                        loc.getFile(),
                                        loc.getStart(),
                                        loc.getEnd());
                                copy.setMessage("`" + missingId + "` defined here");
                                copy.setSecondary(secondaryChain);
                                secondaryChain = copy;
                                break; // one secondary per missing id is enough
                            }
                        }
                    }
                }

                // Primary location is the file itself.
                Location primary = Location.create(file);
                if (secondaryChain != null) {
                    primary.setSecondary(secondaryChain);
                }

                // Build the message.
                StringBuilder sb = new StringBuilder();
                sb.append("The id(s) ");
                for (int i = 0; i < sortedMissing.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append("`").append(sortedMissing.get(i)).append("`");
                }
                sb.append(" are missing from layout `")
                        .append(layoutName)
                        .append("` in some configurations");

                context.report(INCONSISTENT_IDS, primary, sb.toString());
            }
        }
    }

    /**
     * Returns the base name (without extension) for the given layout file.
     *
     * @param file the layout file
     * @return the base name
     */
    private static String getLayoutName(@NonNull File file) {
        String name = file.getName();
        int dot = name.indexOf('.');
        if (dot != -1) {
            name = name.substring(0, dot);
        }
        return name;
    }
}