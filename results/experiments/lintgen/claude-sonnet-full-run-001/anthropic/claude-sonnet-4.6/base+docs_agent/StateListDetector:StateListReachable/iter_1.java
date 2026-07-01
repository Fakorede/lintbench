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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

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

    public StateListDetector() {
    }

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
        // Collect all child <item> elements
        List<Element> items = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;
            String tag = item.getLocalName();
            if (tag == null) {
                tag = item.getTagName();
            }
            if (TAG_ITEM.equals(tag)) {
                items.add(item);
            }
        }

        // Check each item: if it's a catch-all and not the last item, report it
        for (int i = 0; i < items.size() - 1; i++) {
            Element item = items.get(i);
            if (isCatchAll(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is a catch-all and will make all subsequent items unreachable");
            }
        }

        // Also report items that come after a catch-all as unreachable
        boolean foundCatchAll = false;
        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            if (foundCatchAll) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item is unreachable because a previous item is a catch-all " +
                        "(has no state qualifier)");
            } else if (isCatchAll(item) && i < items.size() - 1) {
                foundCatchAll = true;
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
}