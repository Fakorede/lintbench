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
 * Checks for unreachable states in a {@code <selector>}.
 */
public class StateListDetector extends ResourceXmlDetector {

    /** The main issue surfaced by this detector */
    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a state " +
            "qualifier. If not, all subsequent items in the list will be ignored since the " +
            "given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    StateListDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link StateListDetector} */
    public StateListDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Look at the children of the selector element.
        // If any child (other than the last one) has no state attributes,
        // then subsequent children are unreachable.
        NodeList childNodes = element.getChildNodes();
        int childCount = childNodes.getLength();

        Element catchAllItem = null;

        for (int i = 0; i < childCount; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) child;

            if (catchAllItem != null) {
                // We already found a catch-all item earlier; this item is unreachable.
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #"
                                + getChildIndex(catchAllItem)
                                + ") is a catch-all (has no state attributes)");
            } else if (!hasStateAttribute(item)) {
                // Check whether this is the last element child
                if (hasMoreElementSiblings(item)) {
                    // Not the last one — record it as the catch-all
                    catchAllItem = item;
                }
            }
        }
    }

    /**
     * Returns true if the given {@code <item>} element has at least one
     * {@code android:state_*} attribute.
     */
    private static boolean hasStateAttribute(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes != null) {
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attr = (Attr) attributes.item(i);
                if (attr.getLocalName() != null
                        && attr.getLocalName().startsWith("state_")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given element has more element siblings after it
     * within the same parent.
     */
    private static boolean hasMoreElementSiblings(@NonNull Element element) {
        Node sibling = element.getNextSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
            sibling = sibling.getNextSibling();
        }
        return false;
    }

    /**
     * Returns the 1-based index of this element among its element siblings.
     */
    private static int getChildIndex(@NonNull Element element) {
        int index = 1;
        Node sibling = element.getPreviousSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                index++;
            }
            sibling = sibling.getPreviousSibling();
        }
        return index;
    }
}