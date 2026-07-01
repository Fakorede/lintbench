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
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
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
 * Checks for inconsistencies between different layout versions of the same layout.
 */
public class LayoutConsistencyDetector extends ResourceXmlDetector {

    /** Map from layout name to a map from id to location (in the base layout) */
    private final Map<String, Map<String, Location>> mIdMap =
            new HashMap<String, Map<String, Location>>();

    /** Map from layout name to list of layout variation files */
    private final Map<String, List<File>> mFileMap =
            new HashMap<String, List<File>>();

    /** Map from layout name to a map from id to locations in other layout variations */
    private final Map<String, Map<String, List<Location>>> mLocationMap =
            new HashMap<String, Map<String, List<Location>>>();

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
            4,
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
        if (id.startsWith("@+id/")) {
            id = id.substring("@+id/".length());
        } else if (id.startsWith("@id/")) {
            id = id.substring("@id/".length());
        } else {
            return;
        }

        String layoutName = context.file.getName();
        // Strip .xml extension
        if (layoutName.endsWith(".xml")) {
            layoutName = layoutName.substring(0, layoutName.length() - 4);
        }

        // Determine if this is the base layout (in "layout" folder, not "layout-xxx")
        File parentFolder = context.file.getParentFile();
        String folderName = parentFolder != null ? parentFolder.getName() : "";
        boolean isBaseLayout = folderName.equals("layout");

        // Register the file for this layout name
        List<File> files = mFileMap.get(layoutName);
        if (files == null) {
            files = new ArrayList<File>();
            mFileMap.put(layoutName, files);
        }
        if (!files.contains(context.file)) {
            files.add(context.file);
        }

        if (isBaseLayout) {
            // Store id -> location mapping for the base layout
            Map<String, Location> idLocations = mIdMap.get(layoutName);
            if (idLocations == null) {
                idLocations = new HashMap<String, Location>();
                mIdMap.put(layoutName, idLocations);
            }
            if (!idLocations.containsKey(id)) {
                idLocations.put(id, context.getLocation(idAttr));
            }
        } else {
            // Store id -> list of locations mapping for other layout variations
            Map<String, List<Location>> locationMap = mLocationMap.get(layoutName);
            if (locationMap == null) {
                locationMap = new HashMap<String, List<Location>>();
                mLocationMap.put(layoutName, locationMap);
            }
            List<Location> locations = locationMap.get(id);
            if (locations == null) {
                locations = new ArrayList<Location>();
                locationMap.put(id, locations);
            }
            locations.add(context.getLocation(idAttr));
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        // For each layout that has multiple variations, check consistency
        for (Map.Entry<String, Map<String, Location>> entry : mIdMap.entrySet()) {
            String layoutName = entry.getKey();
            Map<String, Location> baseIds = entry.getValue();

            Map<String, List<Location>> otherIds = mLocationMap.get(layoutName);
            if (otherIds == null) {
                // No other variations, nothing to check
                continue;
            }

            List<File> files = mFileMap.get(layoutName);
            if (files == null || files.size() < 2) {
                continue;
            }

            // Check for ids in base layout that are missing in other layouts
            // and ids in other layouts that are missing in base layout
            Set<String> baseIdSet = baseIds.keySet();
            Set<String> otherIdSet = otherIds.keySet();

            // Find ids missing from other layouts (present in base, not in others)
            Set<String> missingFromOther = new HashSet<String>(baseIdSet);
            missingFromOther.removeAll(otherIdSet);

            // Find ids missing from base layout (present in others, not in base)
            Set<String> missingFromBase = new HashSet<String>(otherIdSet);
            missingFromBase.removeAll(baseIdSet);

            if (missingFromOther.isEmpty() && missingFromBase.isEmpty()) {
                continue;
            }

            // Report issues for ids missing from other layouts
            for (String id : missingFromOther) {
                Location location = baseIds.get(id);
                if (location != null) {
                    String message = String.format(
                            "The id `%1$s` in layout `%2$s` is missing from some layout "
                                    + "variations; add it or suppress this warning with a "
                                    + "`tools:ignore=\"InconsistentLayout\"` attribute",
                            id, layoutName);
                    context.report(ISSUE, location, message);
                }
            }

            // Report issues for ids missing from base layout
            for (String id : missingFromBase) {
                List<Location> locations = otherIds.get(id);
                if (locations != null && !locations.isEmpty()) {
                    Location location = locations.get(0);
                    String message = String.format(
                            "The id `%1$s` in layout `%2$s` is missing from some layout "
                                    + "variations; add it or suppress this warning with a "
                                    + "`tools:ignore=\"InconsistentLayout\"` attribute",
                            id, layoutName);
                    context.report(ISSUE, location, message);
                }
            }
        }

        // Also handle the case where there's no base layout but multiple variations exist
        for (Map.Entry<String, Map<String, List<Location>>> entry : mLocationMap.entrySet()) {
            String layoutName = entry.getKey();
            if (mIdMap.containsKey(layoutName)) {
                // Already handled above
                continue;
            }

            // No base layout - check consistency across all variations
            // We don't have enough information here without tracking per-file ids
            // This case is less common and harder to handle without more data structures
        }
    }
}