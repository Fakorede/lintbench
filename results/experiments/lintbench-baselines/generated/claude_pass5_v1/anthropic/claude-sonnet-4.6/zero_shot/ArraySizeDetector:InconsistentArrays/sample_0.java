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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks for inconsistencies in array sizes across different resource configurations.
 */
public class ArraySizeDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue INCONSISTENT = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n" +
            "\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n" +
            "\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    ArraySizeDetector.class,
                    Scope.ALL_RESOURCES_SCOPE));

    /**
     * Map from array name to a list of pairs (file, count) recording
     * how many items each array declaration has in each file.
     */
    private Map<String, List<ArrayCount>> mArrays;

    /** Constructs a new {@link ArraySizeDetector} */
    public ArraySizeDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "string-array",
                "integer-array",
                "array"
        );
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mArrays = new HashMap<String, List<ArrayCount>>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }
        String name = nameAttr.getValue();
        if (name == null || name.isEmpty()) {
            return;
        }

        // Count child <item> elements
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }

        List<ArrayCount> list = mArrays.get(name);
        if (list == null) {
            list = new ArrayList<ArrayCount>();
            mArrays.put(name, list);
        }

        Location location = context.getLocation(element);
        list.add(new ArrayCount(context.file, count, location));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mArrays == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayCount>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
            List<ArrayCount> counts = entry.getValue();

            if (counts.size() < 2) {
                // Only declared in one configuration; nothing to compare
                continue;
            }

            // Find the most common count (preferably from the default folder)
            // and check for inconsistencies
            int defaultCount = -1;
            File defaultFile = null;
            Location defaultLocation = null;

            // Try to find the default (non-locale-specific) values folder entry
            for (ArrayCount ac : counts) {
                String parentName = ac.file.getParentFile().getName();
                if (parentName.equals("values")) {
                    defaultCount = ac.count;
                    defaultFile = ac.file;
                    defaultLocation = ac.location;
                    break;
                }
            }

            // If we didn't find a default, use the first entry
            if (defaultCount == -1) {
                ArrayCount first = counts.get(0);
                defaultCount = first.count;
                defaultFile = first.file;
                defaultLocation = first.location;
            }

            // Check all entries against the default count
            boolean inconsistencyFound = false;
            Location linkedLocation = null;

            for (ArrayCount ac : counts) {
                if (ac.file.equals(defaultFile)) {
                    continue;
                }
                if (ac.count != defaultCount) {
                    if (!inconsistencyFound) {
                        inconsistencyFound = true;
                        linkedLocation = defaultLocation;
                    }

                    // Build secondary location chain
                    Location secondary = ac.location;
                    secondary.setMessage("Declaration with " + ac.count +
                            " array items, file: " + ac.file.getParentFile().getName());
                    secondary.setSecondary(linkedLocation);
                    linkedLocation = secondary;
                }
            }

            if (inconsistencyFound) {
                // Report on the default location (or first location)
                String message = String.format(
                        "Array `%1$s` has an inconsistent number of items (%2$d from `%3$s`, " +
                        "but different counts in other configurations)",
                        name,
                        defaultCount,
                        defaultFile.getParentFile().getName());

                context.report(INCONSISTENT, defaultLocation, message);
            }
        }
    }

    /**
     * Stores a count of array items for a specific file/location.
     */
    private static class ArrayCount {
        public final File file;
        public final int count;
        public final Location location;

        ArrayCount(File file, int count, Location location) {
            this.file = file;
            this.count = count;
            this.location = location;
        }
    }
}