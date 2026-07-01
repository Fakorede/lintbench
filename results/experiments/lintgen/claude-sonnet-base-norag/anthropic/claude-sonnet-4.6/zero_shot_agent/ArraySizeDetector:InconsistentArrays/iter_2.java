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
 * Checks for inconsistencies in array element counts across different resource configurations.
 */
public class ArraySizeDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
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
     * Map from array name to a list of ArrayDeclaration objects recording how many
     * elements each array declaration has in each resource file.
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
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        // Count the number of <item> children
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
        File file = context.file;
        declarations.add(new ArrayDeclaration(file, count, location));
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mArrays == null || mArrays.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String name = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();

            if (declarations.size() < 2) {
                // Only one declaration; nothing to compare
                continue;
            }

            // Find the reference declaration (from the default values folder if possible)
            ArrayDeclaration referenceDeclaration = null;

            for (ArrayDeclaration declaration : declarations) {
                File folder = declaration.file.getParentFile();
                if (folder != null) {
                    String folderName = folder.getName();
                    if (folderName.equals("values")) {
                        referenceDeclaration = declaration;
                        break;
                    }
                }
            }

            // If no default values folder found, use the first declaration
            if (referenceDeclaration == null) {
                referenceDeclaration = declarations.get(0);
            }

            int referenceCount = referenceDeclaration.count;

            // Check all declarations against the reference count
            for (ArrayDeclaration declaration : declarations) {
                if (declaration == referenceDeclaration) {
                    continue;
                }
                if (declaration.count != referenceCount) {
                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in `%3$s`, %4$d in `%5$s`)",
                            name,
                            referenceCount,
                            getRelativePath(referenceDeclaration.file),
                            declaration.count,
                            getRelativePath(declaration.file));

                    context.report(ISSUE, declaration.location, message);
                }
            }
        }
    }

    @NonNull
    private static String getRelativePath(@NonNull File file) {
        File parent = file.getParentFile();
        if (parent != null) {
            return parent.getName() + File.separator + file.getName();
        }
        return file.getName();
    }

    /** Represents a single array declaration in a resource file */
    private static class ArrayDeclaration {
        /** The resource file containing this declaration */
        @NonNull
        public final File file;

        /** The number of items in this array */
        public final int count;

        /** The location of the array element */
        @NonNull
        public final Location location;

        ArrayDeclaration(@NonNull File file, int count, @NonNull Location location) {
            this.file = file;
            this.count = count;
            this.location = location;
        }
    }
}