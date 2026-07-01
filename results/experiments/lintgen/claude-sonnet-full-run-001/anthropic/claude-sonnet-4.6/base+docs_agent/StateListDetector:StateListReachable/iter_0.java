package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class StateListDetector extends Detector implements XmlScanner {

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
                    EnumSet.of(Scope.RESOURCE_FILE)));

    private static final String TAG_SELECTOR = "selector";
    private static final String TAG_ITEM = "item";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public StateListDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SELECTOR);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Iterate over child <item> elements
        NodeList children = element.getChildNodes();
        Element catchAllItem = null;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;
            if (!TAG_ITEM.equals(item.getLocalName()) && !TAG_ITEM.equals(item.getTagName())) {
                continue;
            }

            if (catchAllItem != null) {
                // We already found a catch-all item, and there are more items after it
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item (item #" +
                        getItemIndex(catchAllItem) + ") is a catch-all (has no state qualifier)");
            } else if (isCatchAll(item)) {
                catchAllItem = item;
            }
        }
    }

    /**
     * Returns true if the given item element has no state qualifiers (i.e., no
     * android:state_* attributes), making it a catch-all item.
     */
    private static boolean isCatchAll(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return true;
        }
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
            }
            if (localName != null && localName.startsWith("state_")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the 1-based index of this item among its sibling <item> elements.
     */
    private static int getItemIndex(Element item) {
        int index = 1;
        Node sibling = item.getParentNode().getFirstChild();
        while (sibling != null && sibling != item) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                String tag = sibling.getLocalName();
                if (tag == null) {
                    tag = sibling.getNodeName();
                }
                if (TAG_ITEM.equals(tag)) {
                    index++;
                }
            }
            sibling = sibling.getNextSibling();
        }
        return index;
    }
}