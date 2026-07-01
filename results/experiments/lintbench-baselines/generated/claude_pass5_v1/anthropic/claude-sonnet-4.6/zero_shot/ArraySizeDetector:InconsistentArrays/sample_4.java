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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

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
     * Map from array name to a list of (file, count) pairs describing how many
     * elements that array has in each file it appears in.
     */
    private Map<String, List<ArrayCount>> mArrayCount;

    /** Constructs a new {@link ArraySizeDetector} */
    public ArraySizeDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mArrayCount = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            return;
        }
        String name = nameAttr.getValue();
        if (name.isEmpty()) {
            return;
        }

        // Count the number of child <item> elements
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }

        List<ArrayCount> list = mArrayCount.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mArrayCount.put(name, list);
        }

        Location location = context.getLocation(element);
        list.add(new ArrayCount(context.file, location, count));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mArrayCount == null || mArrayCount.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<ArrayCount>> entry : mArrayCount.entrySet()) {
            String name = entry.getKey();
            List<ArrayCount> counts = entry.getValue();

            if (counts.size() < 2) {
                // Only defined in one configuration, no inconsistency possible
                continue;
            }

            // Check if all counts are the same
            int firstCount = counts.get(0).count;
            boolean allSame = true;
            for (int i = 1; i < counts.size(); i++) {
                if (counts.get(i).count != firstCount) {
                    allSame = false;
                    break;
                }
            }

            if (!allSame) {
                // Find the most common count (likely the "canonical" one)
                // and report inconsistencies
                // Build a description of all the files and their counts
                StringBuilder sb = new StringBuilder();
                sb.append("Array `").append(name).append("` has an inconsistent number of items (");

                // Sort by count to make the message more readable
                List<ArrayCount> sorted = new ArrayList<>(counts);
                Collections.sort(sorted, (a, b) -> {
                    int diff = a.count - b.count;
                    if (diff != 0) return diff;
                    return a.file.getPath().compareTo(b.file.getPath());
                });

                // Find the location to report on (the first one that differs from the majority)
                // and build the full message
                for (int i = 0; i < sorted.size(); i++) {
                    ArrayCount ac = sorted.get(i);
                    String folder = ac.file.getParentFile() != null
                            ? ac.file.getParentFile().getName()
                            : ac.file.getName();
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(folder).append(": ").append(ac.count);
                }
                sb.append(")");

                // Find the location with the minimum count to report as the primary
                // (or the first one alphabetically if tied)
                ArrayCount primary = sorted.get(0);

                // Build secondary locations chain
                Location primaryLocation = primary.location;
                Location prev = null;
                Location chainHead = null;

                for (int i = sorted.size() - 1; i >= 1; i--) {
                    ArrayCount ac = sorted.get(i);
                    String folder = ac.file.getParentFile() != null
                            ? ac.file.getParentFile().getName()
                            : ac.file.getName();
                    Location loc = ac.location.withMessage(folder + ": " + ac.count + " items");
                    if (chainHead == null) {
                        chainHead = loc;
                        prev = loc;
                    } else {
                        prev.setSecondary(loc);
                        prev = loc;
                    }
                }

                if (chainHead != null) {
                    primaryLocation = primaryLocation.withMessage(
                            primary.file.getParentFile() != null
                                    ? primary.file.getParentFile().getName() + ": "
                                            + primary.count + " items"
                                    : primary.count + " items");
                    primaryLocation.setSecondary(chainHead);
                }

                context.report(INCONSISTENT, primaryLocation, sb.toString());
            }
        }
    }

    /**
     * Stores information about an array declaration in a specific file.
     */
    private static class ArrayCount {
        /** The resource file */
        @NonNull
        public final File file;

        /** The location of the array declaration */
        @NonNull
        public final Location location;

        /** The number of items in the array */
        public final int count;

        ArrayCount(@NonNull File file, @NonNull Location location, int count) {
            this.file = file;
            this.location = location;
            this.count = count;
        }
    }
}