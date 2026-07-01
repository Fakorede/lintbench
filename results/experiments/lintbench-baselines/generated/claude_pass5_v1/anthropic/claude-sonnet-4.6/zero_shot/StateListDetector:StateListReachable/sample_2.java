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
 */
public class StateListDetector extends ResourceXmlDetector {

    /** The main issue: unreachable state in a selector */
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

    private static final String TAG_SELECTOR = "selector";
    private static final String TAG_ITEM = "item";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Constructs a new {@link StateListDetector} */
    public StateListDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SELECTOR);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Look at all child <item> elements of the <selector>
        NodeList children = element.getChildNodes();
        int childCount = children.getLength();

        // We need to find any <item> that has no state attributes and is NOT the last item.
        // After such an item, all subsequent items are unreachable.

        Element catchAllItem = null;

        for (int i = 0; i < childCount; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) node;
            if (!TAG_ITEM.equals(item.getLocalName()) && !TAG_ITEM.equals(item.getTagName())) {
                continue;
            }

            if (catchAllItem != null) {
                // This item comes after a catch-all item: it is unreachable
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #"
                                + getItemIndex(catchAllItem)
                                + ") is a catch-all (has no state attributes)");
            } else if (!hasStateAttribute(item)) {
                // Check if this is truly the last <item> element
                if (hasSubsequentItemElement(children, i)) {
                    // There are more items after this one, so this is a problem
                    catchAllItem = item;
                    context.report(
                            ISSUE,
                            item,
                            context.getLocation(item),
                            "This item will match all states because it has no state "
                                    + "attributes, and all subsequent items will be unreachable");
                }
                // If it's the last item, it's fine — the default/catch-all belongs at the end
            }
        }
    }

    /**
     * Returns true if the given {@code <item>} element has at least one
     * {@code android:state_*} attribute.
     */
    private static boolean hasStateAttribute(@NonNull Element item) {
        NamedNodeMap attrs = item.getAttributes();
        if (attrs == null) {
            return false;
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
                // strip namespace prefix if present
                int colon = localName.indexOf(':');
                if (colon >= 0) {
                    localName = localName.substring(colon + 1);
                }
            }
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if there is at least one {@code <item>} element after position
     * {@code currentIndex} in the given {@link NodeList}.
     */
    private static boolean hasSubsequentItemElement(@NonNull NodeList children, int currentIndex) {
        int childCount = children.getLength();
        for (int i = currentIndex + 1; i < childCount; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            if (TAG_ITEM.equals(el.getLocalName()) || TAG_ITEM.equals(el.getTagName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a 1-based index of the given {@code <item>} element among its sibling
     * {@code <item>} elements, for use in error messages.
     */
    private static int getItemIndex(@NonNull Element item) {
        Node parent = item.getParentNode();
        if (parent == null) {
            return -1;
        }
        NodeList siblings = parent.getChildNodes();
        int itemIndex = 0;
        for (int i = 0; i < siblings.getLength(); i++) {
            Node sibling = siblings.item(i);
            if (sibling.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) sibling;
            if (TAG_ITEM.equals(el.getLocalName()) || TAG_ITEM.equals(el.getTagName())) {
                itemIndex++;
                if (el == item) {
                    return itemIndex;
                }
            }
        }
        return -1;
    }
}