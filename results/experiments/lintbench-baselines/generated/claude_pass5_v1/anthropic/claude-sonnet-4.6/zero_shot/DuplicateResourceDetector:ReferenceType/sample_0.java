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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;

/**
 * Checks for duplicate resources and incorrect reference types in resource alias definitions.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue: incorrect reference type in resource alias */
    public static final Issue TYPE_MISMATCH = Issue.create(
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

    /** Constructs a new {@link DuplicateResourceDetector} */
    public DuplicateResourceDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're looking for <item type="X" name="Y">@Z/name</item> constructs
        // where Z != X (type mismatch in resource alias)

        Attr typeAttr = element.getAttributeNode(ATTR_TYPE);
        if (typeAttr == null) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        // Get the text content of the element
        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        text = text.trim();

        // Check if the value is a resource reference
        if (!text.startsWith("@")) {
            return;
        }

        // Parse the reference: @[+][package:]type/name or @type/name
        String reference = text;

        // Strip leading @
        reference = reference.substring(1);

        // Strip optional + (for new id creation, but that's unusual in aliases)
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Strip optional package prefix (e.g., "android:")
        int colonIndex = reference.indexOf(':');
        if (colonIndex != -1) {
            reference = reference.substring(colonIndex + 1);
        }

        // Now reference should be "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex).trim();
        if (referencedType.isEmpty()) {
            return;
        }

        String declaredType = typeAttr.getValue().trim();
        if (declaredType.isEmpty()) {
            return;
        }

        // Normalize the types for comparison
        // Some types have aliases (e.g., "integer" vs "int" in some contexts),
        // but generally we do a direct comparison.
        if (!referencedType.equals(declaredType)) {
            // Validate that both are known resource types to avoid false positives
            ResourceType declaredResourceType = ResourceType.fromXmlValue(declaredType);
            ResourceType referencedResourceType = ResourceType.fromXmlValue(referencedType);

            if (declaredResourceType != null && referencedResourceType != null) {
                String message = String.format(
                        "Wrong resource type: expected value of type `@%1$s`, got `@%2$s`",
                        declaredType, referencedType);
                context.report(TYPE_MISMATCH, element, context.getLocation(element), message);
            }
        }
    }
}