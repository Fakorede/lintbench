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
     * Map from array name to a list of (file, count) pairs recording how many
     * items each array declaration has in each file.
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
        return Arrays.asList(
                "string-array",
                "integer-array",
                "array"
        );
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mArrayCount = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr == null) {
            return;
        }
        String name = nameAttr.getValue();
        if (name.isEmpty()) {
            return;
        }

        // Count the number of item children
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
        list.add(new ArrayCount(context.file, count, location));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mArrayCount == null) {
            return;
        }

        for (Map.Entry<String, List<ArrayCount>> entry : mArrayCount.entrySet()) {
            String name = entry.getKey();
            List<ArrayCount> counts = entry.getValue();

            if (counts.size() < 2) {
                // Only declared in one configuration; no inconsistency possible
                continue;
            }

            // Find the most common count (use the first one as reference, typically default)
            // and check if all counts are the same
            int referenceCount = counts.get(0).count;
            boolean allSame = true;
            int maxCount = referenceCount;
            int minCount = referenceCount;

            for (ArrayCount ac : counts) {
                if (ac.count != referenceCount) {
                    allSame = false;
                }
                if (ac.count > maxCount) {
                    maxCount = ac.count;
                }
                if (ac.count < minCount) {
                    minCount = ac.count;
                }
            }

            if (allSame) {
                continue;
            }

            // Build a message describing the inconsistency
            StringBuilder sb = new StringBuilder();
            sb.append("Array `").append(name).append("` has an inconsistent number of items (");

            // List all the counts per file
            for (int i = 0; i < counts.size(); i++) {
                ArrayCount ac = counts.get(i);
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(getFolderName(ac.file)).append(": ").append(ac.count);
            }
            sb.append(")");

            // Report on each location that differs from the first/reference
            // Find the location with the most common count to use as primary
            // We'll report on each non-default location
            Location primaryLocation = counts.get(0).location;
            String message = sb.toString();

            // Build a chain of secondary locations
            Location secondary = null;
            for (int i = counts.size() - 1; i >= 1; i--) {
                ArrayCount ac = counts.get(i);
                Location loc = ac.location;
                if (secondary != null) {
                    loc.setSecondary(secondary);
                }
                loc.setMessage(getFolderName(ac.file) + ": " + ac.count + " items");
                secondary = loc;
            }

            if (secondary != null) {
                primaryLocation.setSecondary(secondary);
            }

            context.report(INCONSISTENT, primaryLocation, message);
        }

        mArrayCount = null;
    }

    /**
     * Returns the folder name portion of the given file's parent directory.
     */
    @NonNull
    private static String getFolderName(@Nullable File file) {
        if (file == null) {
            return "?";
        }
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName();
        }
        return file.getName();
    }

    /**
     * Simple holder for an array count in a specific file.
     */
    private static class ArrayCount {
        /** The resource file containing the array declaration */
        @NonNull
        public final File file;

        /** The number of items in the array */
        public final int count;

        /** The location of the array element */
        @NonNull
        public final Location location;

        ArrayCount(@NonNull File file, int count, @NonNull Location location) {
            this.file = file;
            this.count = count;
            this.location = location;
        }
    }
}