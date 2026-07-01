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
     * Map from array name to a list of (file, count) pairs representing each
     * declaration of that array across resource files.
     */
    private Map<String, List<ArrayDeclaration>> mArrays;

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
        mArrays = new HashMap<>();
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

        // Count child <item> elements
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }

        List<ArrayDeclaration> declarations = mArrays.get(name);
        if (declarations == null) {
            declarations = new ArrayList<>();
            mArrays.put(name, declarations);
        }

        Location location = context.getLocation(element);
        declarations.add(new ArrayDeclaration(context.file, count, location));
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mArrays == null || mArrays.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();

            if (declarations.size() < 2) {
                // Only one declaration, nothing to compare
                continue;
            }

            // Find if there are inconsistencies in counts
            int firstCount = declarations.get(0).count;
            boolean inconsistent = false;
            for (int i = 1; i < declarations.size(); i++) {
                if (declarations.get(i).count != firstCount) {
                    inconsistent = true;
                    break;
                }
            }

            if (!inconsistent) {
                continue;
            }

            // Find the declaration with the highest count to use as reference
            // (typically the default/base locale)
            ArrayDeclaration largest = declarations.get(0);
            for (ArrayDeclaration decl : declarations) {
                if (decl.count > largest.count) {
                    largest = decl;
                }
            }

            // Build a message listing the inconsistencies
            StringBuilder sb = new StringBuilder();
            sb.append("Array `").append(name).append("` has an inconsistent number of items (");

            // Sort by file path for deterministic output
            List<ArrayDeclaration> sorted = new ArrayList<>(declarations);
            Collections.sort(sorted, (a, b) -> {
                String pathA = getRelativePath(a.file);
                String pathB = getRelativePath(b.file);
                return pathA.compareTo(pathB);
            });

            boolean first = true;
            for (ArrayDeclaration decl : sorted) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(getRelativePath(decl.file)).append(": ").append(decl.count);
            }
            sb.append(")");

            // Report at the location of the declaration with the most items
            // (or the first one if counts are equal in some)
            context.report(INCONSISTENT, largest.location, sb.toString());
        }
    }

    /**
     * Returns a shortened relative path for display purposes.
     * Tries to show just the res/values-xx/filename portion.
     */
    private static String getRelativePath(@NonNull File file) {
        String path = file.getPath();
        // Normalize separators
        path = path.replace('\\', '/');

        // Try to find "res/" in the path and return from there
        int resIndex = path.lastIndexOf("/res/");
        if (resIndex >= 0) {
            return path.substring(resIndex + 1);
        }

        // Otherwise just return the parent directory name + file name
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName() + "/" + file.getName();
        }

        return file.getName();
    }

    /**
     * Represents a single declaration of an array in a resource file.
     */
    private static class ArrayDeclaration {
        /** The file containing this declaration */
        @NonNull
        public final File file;

        /** The number of items in this array */
        public final int count;

        /** The location of this declaration */
        @NonNull
        public final Location location;

        ArrayDeclaration(@NonNull File file, int count, @NonNull Location location) {
            this.file = file;
            this.count = count;
            this.location = location;
        }
    }
}