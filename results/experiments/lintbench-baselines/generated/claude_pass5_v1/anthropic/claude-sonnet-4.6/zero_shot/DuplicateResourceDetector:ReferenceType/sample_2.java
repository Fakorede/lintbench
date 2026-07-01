/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for duplicate resources and incorrect resource alias types.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue TYPE_MISMATCH = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be " +
            "of the same type as the alias",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_TYPE = "type";
    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

    /**
     * Constructs a new {@link DuplicateResourceDetector}
     */
    public DuplicateResourceDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're looking for <item type="..." name="...">@type/name</item>
        // where the reference type doesn't match the item type

        String type = element.getAttribute(ATTR_TYPE);
        if (type == null || type.isEmpty()) {
            return;
        }

        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        // Check if the value is a resource reference
        if (!value.startsWith("@")) {
            return;
        }

        // Strip leading @ and optional + (for new IDs)
        String reference = value.substring(1);
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Strip package prefix if present (e.g., "android:drawable/foo" -> "drawable/foo")
        int colonIndex = reference.indexOf(':');
        if (colonIndex != -1) {
            reference = reference.substring(colonIndex + 1);
        }

        // Now reference should be "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referenceType = reference.substring(0, slashIndex);

        // Normalize types for comparison
        // Some types have aliases (e.g., "color" vs "color")
        if (!referenceType.equals(type)) {
            // Check if these are compatible types
            if (!areCompatibleTypes(type, referenceType)) {
                String referenceName = reference.substring(slashIndex + 1);
                String name = element.getAttribute(ATTR_NAME);

                Attr typeAttr = element.getAttributeNode(ATTR_TYPE);
                if (typeAttr != null) {
                    context.report(
                            TYPE_MISMATCH,
                            element,
                            context.getLocation(typeAttr),
                            String.format(
                                    "Wrong type for resource alias `%1$s`: expected `%2$s`, was `%3$s`",
                                    name, type, referenceType));
                } else {
                    context.report(
                            TYPE_MISMATCH,
                            element,
                            context.getLocation(element),
                            String.format(
                                    "Wrong type for resource alias `%1$s`: expected `%2$s`, was `%3$s`",
                                    name, type, referenceType));
                }
            }
        }
    }

    /**
     * Returns true if the two resource types are compatible for aliasing purposes.
     *
     * @param aliasType     the type declared on the alias item element
     * @param referenceType the type of the referenced resource
     * @return true if the types are compatible
     */
    private static boolean areCompatibleTypes(
            @NonNull String aliasType,
            @NonNull String referenceType) {
        // Exact match is always fine
        if (aliasType.equals(referenceType)) {
            return true;
        }

        // drawable and mipmap are sometimes used interchangeably for aliases
        if ((aliasType.equals(ResourceType.DRAWABLE.getName())
                        && referenceType.equals(ResourceType.MIPMAP.getName()))
                || (aliasType.equals(ResourceType.MIPMAP.getName())
                        && referenceType.equals(ResourceType.DRAWABLE.getName()))) {
            return true;
        }

        return false;
    }
}