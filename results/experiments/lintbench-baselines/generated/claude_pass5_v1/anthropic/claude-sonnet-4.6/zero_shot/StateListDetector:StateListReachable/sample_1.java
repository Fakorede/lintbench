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

    /** The main issue surfaced by this detector */
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

    /** The Android namespace URI */
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link StateListDetector} */
    public StateListDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Iterate over the children of the <selector> element.
        // If we find an item that has no state attributes, and it is NOT the
        // last item, report the items after it as unreachable.
        NodeList children = element.getChildNodes();
        int n = children.getLength();

        // Track the element node that has no state qualifier (catch-all)
        Element catchAll = null;

        for (int i = 0; i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;

            if (catchAll != null) {
                // We already saw a catch-all earlier; this item is unreachable.
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #"
                                + getChildIndex(catchAll)
                                + ") is a catch-all (has no state qualifier)");
            } else if (!hasStateAttribute(item)) {
                // This item has no state qualifier – it's a catch-all.
                // Remember it so we can flag subsequent items.
                catchAll = item;
            }
        }
    }

    /**
     * Returns {@code true} if the given {@code <item>} element has at least one
     * {@code android:state_*} attribute.
     */
    private static boolean hasStateAttribute(@NonNull Element item) {
        NamedNodeMap attrs = item.getAttributes();
        if (attrs == null) {
            return false;
        }
        for (int i = 0, n = attrs.getLength(); i < n; i++) {
            Attr attr = (Attr) attrs.item(i);
            String localName = attr.getLocalName();
            String ns = attr.getNamespaceURI();
            if (localName != null
                    && localName.startsWith("state_")
                    && ANDROID_NS.equals(ns)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the 1-based index of {@code element} among its sibling element
     * nodes (i.e. ignoring text/comment nodes).
     */
    private static int getChildIndex(@NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent == null) {
            return 1;
        }
        NodeList siblings = parent.getChildNodes();
        int index = 0;
        for (int i = 0, n = siblings.getLength(); i < n; i++) {
            Node sibling = siblings.item(i);
            if (sibling.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            index++;
            if (sibling == element) {
                return index;
            }
        }
        return index;
    }
}