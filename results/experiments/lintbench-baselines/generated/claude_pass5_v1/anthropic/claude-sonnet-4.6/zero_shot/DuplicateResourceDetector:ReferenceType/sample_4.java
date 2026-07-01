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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for duplicate resources and incorrect reference types in resource aliases.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue: incorrect reference type used in a resource alias */
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
        // We're looking for resource alias definitions, e.g.:
        //   <item type="drawable" name="foo">@color/bar</item>
        // In this case, the alias type (drawable) doesn't match the reference type (color).

        String typeAttr = element.getAttribute("type");
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        // Get the text content of the element (the reference value)
        String text = getTextContent(element);
        if (text == null) {
            return;
        }
        text = text.trim();

        // Check if it's a resource reference
        if (!text.startsWith("@")) {
            return;
        }

        // Remove the leading '@'
        String reference = text.substring(1);

        // Handle the case where reference might start with '+' (e.g., @+id/foo)
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Split by '/' to get type and name
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex).trim();

        // Strip any package prefix (e.g., "android:drawable" -> "drawable")
        int colonIndex = referencedType.indexOf(':');
        if (colonIndex != -1) {
            referencedType = referencedType.substring(colonIndex + 1);
        }

        // Normalize the alias type
        String aliasType = typeAttr.trim();

        // Strip any package prefix from alias type as well
        int aliasColonIndex = aliasType.indexOf(':');
        if (aliasColonIndex != -1) {
            aliasType = aliasType.substring(aliasColonIndex + 1);
        }

        // Compare the types
        if (!aliasType.isEmpty() && !referencedType.isEmpty()
                && !aliasType.equals(referencedType)) {
            // Check if they are compatible types
            if (!areCompatibleTypes(aliasType, referencedType)) {
                String nameAttr = element.getAttribute("name");
                String resourceName = nameAttr != null && !nameAttr.isEmpty()
                        ? nameAttr : "(unknown)";

                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "Wrong resource type: alias `%1$s` is of type `%2$s` but "
                                        + "references `%3$s`",
                                resourceName, aliasType, referencedType));
            }
        }
    }

    /**
     * Returns the text content of an element (direct text children only).
     */
    private static String getTextContent(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        if (children == null || children.getLength() == 0) {
            return null;
        }

        StringBuilder sb = null;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String value = child.getNodeValue();
                if (value != null && !value.isEmpty()) {
                    if (sb == null) {
                        sb = new StringBuilder();
                    }
                    sb.append(value);
                }
            }
        }

        return sb != null ? sb.toString() : null;
    }

    /**
     * Checks whether two resource types are compatible for aliasing purposes.
     * Some resource types are considered compatible (e.g., "integer" and "bool"
     * may both be referenced as each other in certain contexts, but generally
     * they should match).
     */
    private static boolean areCompatibleTypes(@NonNull String aliasType,
            @NonNull String referencedType) {
        // Exact match is always compatible
        if (aliasType.equals(referencedType)) {
            return true;
        }

        // Try to look up the ResourceType for both
        ResourceType aliasResourceType = ResourceType.fromXmlValue(aliasType);
        ResourceType referencedResourceType = ResourceType.fromXmlValue(referencedType);

        if (aliasResourceType == null || referencedResourceType == null) {
            // If we can't determine the types, don't flag it
            return true;
        }

        // They must be exactly the same type
        return aliasResourceType == referencedResourceType;
    }
}