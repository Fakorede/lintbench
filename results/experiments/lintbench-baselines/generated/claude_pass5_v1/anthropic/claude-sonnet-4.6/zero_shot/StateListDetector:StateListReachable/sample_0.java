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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Checks for unreachable states in state-list drawables / selectors.
 */
public class StateListDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a state " +
            "qualifier. If not, all subsequent items in the list will be ignored since " +
            "the given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    StateListDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String SELECTOR_TAG = "selector";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link StateListDetector} */
    public StateListDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SELECTOR_TAG);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Iterate through all child elements of the <selector>
        NodeList children = element.getChildNodes();
        int childCount = children.getLength();

        Element matchAllElement = null;

        for (int i = 0; i < childCount; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) node;

            if (matchAllElement != null) {
                // We already found a match-all item earlier; this item is unreachable
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #"
                                + getItemNumber(element, matchAllElement)
                                + ") is a more general match than this one");
            } else if (!hasStateAttributes(item)) {
                // This item has no state qualifiers — it matches everything
                matchAllElement = item;
            }
        }
    }

    /**
     * Returns whether the given element has any state-related attributes
     * (i.e., attributes in the Android namespace that start with "state_").
     */
    private static boolean hasStateAttributes(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                String ns = attr.getNamespaceURI();
                if (ANDROID_NS.equals(ns)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the 1-based index of the given child element within its parent selector element,
     * counting only element nodes.
     */
    private static int getItemNumber(@NonNull Element selector, @NonNull Element item) {
        NodeList children = selector.getChildNodes();
        int count = 0;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            count++;
            if (node == item) {
                return count;
            }
        }
        return -1;
    }
}