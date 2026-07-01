/*
 * Copyright (C) 2011 The Android Open Source Project
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
import static com.android.SdkConstants.VIEW_INCLUDE;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for duplicate ids within a single layout file.
 */
public class DuplicateIdDetector extends LayoutDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can " +
            " return an unexpected view.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    DuplicateIdDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Map from id to the first location where it was seen */
    private final Map<String, Location> mIdToLocation = new HashMap<>();

    /** Map from id to a list of all duplicate locations (only populated once a duplicate is found) */
    private final Map<String, List<Location>> mIdToLocations = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIdToLocation.clear();
        mIdToLocations.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Report all duplicates
        for (Map.Entry<String, List<Location>> entry : mIdToLocations.entrySet()) {
            String id = entry.getKey();
            List<Location> locations = entry.getValue();

            // The primary location is the first occurrence; duplicates are chained
            // We report on the duplicate (second+) occurrences, pointing back to the first
            Location firstLocation = mIdToLocation.get(id);

            // Build a chain: last duplicate -> ... -> second -> first
            // We report on the last duplicate, with a chain back to first
            // Actually let's report each duplicate occurrence pointing to the first
            for (int i = locations.size() - 1; i >= 0; i--) {
                Location duplicateLocation = locations.get(i);
                if (i > 0) {
                    duplicateLocation.setSecondary(locations.get(i - 1));
                } else {
                    duplicateLocation.setSecondary(firstLocation);
                }
            }

            Location last = locations.get(locations.size() - 1);
            String message = String.format("Duplicate id `%1$s`, already defined earlier in this layout", id);
            ((XmlContext) context).report(ISSUE, last, message);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Skip ids on <include> tags (the id there overrides the root id of the included layout)
        Element element = attribute.getOwnerElement();
        if (element.getTagName().equals(VIEW_INCLUDE)) {
            // Check the layout attribute to see if it's an include
            // We still want to track the id for includes, but typically includes
            // can have duplicate ids intentionally in some cases.
            // For safety, let's still check includes.
        }

        String id = attribute.getValue();
        if (id == null || id.isEmpty()) {
            return;
        }

        if (mIdToLocation.containsKey(id)) {
            // This is a duplicate
            List<Location> locations = mIdToLocations.get(id);
            if (locations == null) {
                locations = new ArrayList<>();
                mIdToLocations.put(id, locations);
            }
            locations.add(context.getLocation(attribute));
        } else {
            mIdToLocation.put(id, context.getLocation(attribute));
        }
    }
}