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
 * Checks for duplicate resources and incorrect reference types in resource aliases.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue: incorrect reference type in resource alias */
    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be " +
            "of the same type as the alias.",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_TYPE = "type";
    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

    /** Constructs a new {@link DuplicateResourceDetector} */
    public DuplicateResourceDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We only care about <item> elements that have a type attribute (resource aliases)
        String typeAttr = element.getAttribute(ATTR_TYPE);
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        // Get the text content of the element - this is the reference value
        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        // Check if the value is a resource reference (@type/name)
        if (!value.startsWith("@")) {
            return;
        }

        // Strip leading @ and optional + (for new IDs)
        String reference = value.substring(1);
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Handle package-qualified references like @android:color/white
        // Strip package prefix if present
        int colonIndex = reference.indexOf(':');
        if (colonIndex != -1) {
            reference = reference.substring(colonIndex + 1);
        }

        // Now reference should be "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Compare the declared type with the referenced type
        if (!typeAttr.equals(referencedType)) {
            // Check if they are compatible resource types
            if (!areCompatibleTypes(typeAttr, referencedType)) {
                Attr typeNode = element.getAttributeNode(ATTR_TYPE);
                String message = String.format(
                        "Wrong resource type: expected value of type `@%1$s` but got `@%2$s`",
                        typeAttr, referencedType);
                if (typeNode != null) {
                    context.report(ISSUE, element, context.getLocation(typeNode), message);
                } else {
                    context.report(ISSUE, element, context.getLocation(element), message);
                }
            }
        }
    }

    /**
     * Checks whether two resource type strings are compatible for aliasing purposes.
     *
     * @param declaredType the type declared on the alias item element
     * @param referencedType the type of the referenced resource
     * @return true if the types are compatible
     */
    private static boolean areCompatibleTypes(
            @NonNull String declaredType,
            @NonNull String referencedType) {
        // Exact match is always compatible
        if (declaredType.equals(referencedType)) {
            return true;
        }

        // Try to resolve both as ResourceType enums for canonical comparison
        try {
            ResourceType declared = ResourceType.fromXmlValue(declaredType);
            ResourceType referenced = ResourceType.fromXmlValue(referencedType);
            if (declared != null && referenced != null) {
                return declared == referenced;
            }
        } catch (Exception ignore) {
            // If we can't resolve, fall through
        }

        return false;
    }
}