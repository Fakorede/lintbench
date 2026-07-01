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

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SELECTOR);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        boolean foundUnqualifiedItem = false;
        Element unqualifiedItem = null;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) child;
            if (!TAG_ITEM.equals(item.getTagName())) {
                continue;
            }

            if (foundUnqualifiedItem) {
                context.report(ISSUE, item, context.getLocation(item),
                        "This item is unreachable because a previous item (item #" +
                        getItemIndex(element, unqualifiedItem) +
                        ") is not constrained by any state and will always be matched");
            } else if (!hasStateAttribute(item)) {
                foundUnqualifiedItem = true;
                unqualifiedItem = item;
            }
        }
    }

    private static boolean hasStateAttribute(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name != null && name.startsWith("state_")) {
                String ns = attr.getNamespaceURI();
                if (ANDROID_NS.equals(ns)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int getItemIndex(Element selector, Element item) {
        NodeList children = selector.getChildNodes();
        int index = 0;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (TAG_ITEM.equals(((Element) child).getTagName())) {
                index++;
                if (child == item) {
                    return index;
                }
            }
        }
        return index;
    }
}