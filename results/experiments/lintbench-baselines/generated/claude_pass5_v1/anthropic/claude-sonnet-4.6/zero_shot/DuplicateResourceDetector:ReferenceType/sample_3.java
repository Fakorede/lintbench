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

import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
            "of the same type as the alias",
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
        return Arrays.asList(
                "item"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We're looking for resource alias items of the form:
        // <item type="TYPE" name="NAME">@TYPE2/name2</item>
        // where TYPE != TYPE2

        String typeAttr = element.getAttribute("type");
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        // Get the text content of the element (the reference value)
        String text = getTextContent(element);
        if (text == null || text.isEmpty()) {
            return;
        }

        text = text.trim();

        // Check if the value is a resource reference
        if (!text.startsWith("@")) {
            return;
        }

        // Parse the reference: @[package:]type/name
        String reference = text.substring(1); // remove leading @

        // Handle the case where reference starts with another @
        if (reference.startsWith("@")) {
            return;
        }

        // Strip package prefix if present (e.g., "android:color/white" -> "color/white")
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
            // Verify both are known resource types
            ResourceType declaredResourceType = ResourceType.fromXmlValue(typeAttr);
            ResourceType referencedResourceType = ResourceType.fromXmlValue(referencedType);

            if (declaredResourceType != null && referencedResourceType != null) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "Wrong resource type: expected `%1$s` but reference is of type `%2$s`",
                                typeAttr,
                                referencedType));
            }
        }
    }

    /**
     * Returns the text content of an element (direct text children only).
     */
    private static String getTextContent(@NonNull Element element) {
        StringBuilder sb = new StringBuilder();
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE ||
                    child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
            child = child.getNextSibling();
        }
        return sb.toString();
    }
}