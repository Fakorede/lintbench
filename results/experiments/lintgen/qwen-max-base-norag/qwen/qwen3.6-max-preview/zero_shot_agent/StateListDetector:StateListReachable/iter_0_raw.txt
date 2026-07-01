package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class StateListDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a selector",
            "In a selector, only the last child in the state list should omit a state qualifier. " +
            "If not, all subsequent items in the list will be ignored since the given item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        int lastItemIndex = -1;
        int itemCount = 0;

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (isItemElement(node)) {
                lastItemIndex = itemCount;
                itemCount++;
            }
        }

        if (lastItemIndex <= 0) {
            return;
        }

        int currentIndex = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (isItemElement(node)) {
                if (currentIndex < lastItemIndex) {
                    Element item = (Element) node;
                    if (!hasStateQualifier(item)) {
                        context.report(ISSUE, item, context.getLocation(item),
                                "Unreachable state: This item matches all states and is not the last item, " +
                                "so subsequent items will be ignored.");
                    }
                }
                currentIndex++;
            }
        }
    }

    private static boolean isItemElement(Node node) {
        return node.getNodeType() == Node.ELEMENT_NODE && "item".equals(node.getNodeName());
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String ns = attr.getNamespaceURI();
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
            }
            if (SdkConstants.ANDROID_URI.equals(ns) && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}