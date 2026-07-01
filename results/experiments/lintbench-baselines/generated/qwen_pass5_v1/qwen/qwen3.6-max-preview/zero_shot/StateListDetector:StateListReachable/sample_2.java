package com.android.tools.lint.checks;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StateListDetector extends ResourceXmlDetector {
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                items.add((Element) child);
            }
        }

        int count = items.size();
        for (int i = 0; i < count; i++) {
            Element item = items.get(i);
            boolean hasStateQualifier = false;
            NamedNodeMap attrs = item.getAttributes();
            for (int j = 0; j < attrs.getLength(); j++) {
                Attr attr = (Attr) attrs.item(j);
                String ns = attr.getNamespaceURI();
                String name = attr.getLocalName();
                if (name == null) {
                    name = attr.getName();
                }
                if (ANDROID_URI.equals(ns) && name.startsWith("state_")) {
                    hasStateQualifier = true;
                    break;
                }
            }

            if (!hasStateQualifier && i < count - 1) {
                context.report(ISSUE, item, context.getLocation(item),
                        "Unreachable state: this item has no state qualifiers and is not last, " +
                        "so subsequent items will never be matched");
            }
        }
    }
}