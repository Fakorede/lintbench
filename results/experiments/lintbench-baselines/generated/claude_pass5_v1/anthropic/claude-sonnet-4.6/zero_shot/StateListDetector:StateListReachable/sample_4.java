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

import java.util.Collection;
import java.util.Collections;

/**
 * Checks for unreachable states in a {@code <selector>} element.
 * <p>
 * In a selector, only the last child in the state list should omit a state
 * qualifier. If not, all subsequent items in the list will be ignored since the
 * given item will match all.
 */
public class StateListDetector extends ResourceXmlDetector {

    /** The main issue detected by this detector */
    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a state " +
            "qualifier. If not, all subsequent items in the list will be ignored " +
            "since the given item will match all.",
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
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Iterate over the children of the <selector> element
        NodeList children = element.getChildNodes();
        int childCount = children.getLength();

        // Track whether we've already seen a "catch-all" item (an item with no state attributes)
        Element catchAllElement = null;

        for (int i = 0; i < childCount; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) child;

            if (catchAllElement != null) {
                // We already saw a catch-all item before this one — this item is unreachable
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (line "
                                + context.getLocation(catchAllElement).getStart().getLine()
                                + ") is a more general match than this one");
            } else if (!hasStateAttribute(item)) {
                // This item has no state qualifier — it's a catch-all
                catchAllElement = item;
            }
        }
    }

    /**
     * Returns true if the given element has at least one {@code android:state_*} attribute.
     */
    private static boolean hasStateAttribute(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            if (attribute.getLocalName().startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}