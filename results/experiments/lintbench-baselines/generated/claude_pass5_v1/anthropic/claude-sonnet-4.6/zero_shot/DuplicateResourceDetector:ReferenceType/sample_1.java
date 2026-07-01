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
 * Checks for duplicate resource definitions and incorrect reference types in resource aliases.
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue: incorrect reference type in a resource alias */
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
                "item",
                "drawable",
                "color",
                "string",
                "dimen",
                "integer",
                "bool",
                "style",
                "layout",
                "array",
                "string-array",
                "integer-array",
                "plurals",
                "attr"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We are looking for resource aliases: items that reference another resource
        // e.g. <item type="drawable" name="foo">@color/bar</item>
        // In this case, the referenced resource type must match the declared type.

        String tagName = element.getTagName();

        // Determine the declared resource type
        String declaredType = null;
        if (tagName.equals("item")) {
            // <item type="..." name="...">@type/name</item>
            Attr typeAttr = element.getAttributeNode("type");
            if (typeAttr != null) {
                declaredType = typeAttr.getValue();
            }
        } else {
            // The tag itself is the type (e.g. <drawable>, <color>, etc.)
            declaredType = tagName;
        }

        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        // Get the text content of the element
        String textContent = getTextContent(element);
        if (textContent == null) {
            return;
        }

        textContent = textContent.trim();

        // Check if it's a resource reference (@type/name)
        if (!textContent.startsWith("@")) {
            return;
        }

        // Remove leading '@'
        String reference = textContent.substring(1);

        // Handle package-qualified references like @android:type/name
        if (reference.contains(":")) {
            // e.g. "android:drawable/foo" -> take the part after ':'
            reference = reference.substring(reference.indexOf(':') + 1);
        }

        // Now reference should be "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex < 0) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        if (referencedType.isEmpty()) {
            return;
        }

        // Normalize types for comparison
        // Some types have aliases (e.g. "integer-array" vs "array")
        String normalizedDeclared = normalizeType(declaredType);
        String normalizedReferenced = normalizeType(referencedType);

        if (!normalizedDeclared.equals(normalizedReferenced)) {
            String message = String.format(
                    "Wrong resource type: expected value of type `@%1$s` but got `@%2$s`",
                    declaredType,
                    referencedType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    /**
     * Normalizes resource type strings for comparison purposes.
     * For example, "integer-array" and "array" both refer to arrays.
     */
    private static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        switch (type) {
            case "integer-array":
            case "string-array":
                return "array";
            default:
                return type;
        }
    }

    /**
     * Returns the trimmed text content of an element (direct text children only).
     */
    private static String getTextContent(Element element) {
        NodeList children = element.getChildNodes();
        if (children == null || children.getLength() == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE ||
                    child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(child.getNodeValue());
            }
        }

        String text = sb.toString().trim();
        return text.isEmpty() ? null : text;
    }
}