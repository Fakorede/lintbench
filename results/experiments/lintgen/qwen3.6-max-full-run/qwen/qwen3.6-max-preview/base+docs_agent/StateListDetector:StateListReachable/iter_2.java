package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a state qualifier. " +
            "If not, all subsequent items in the list will be ignored since the given item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        boolean foundDefault = false;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) child;
            if (!"item".equals(item.getTagName())) {
                continue;
            }

            if (foundDefault) {
                context.report(ISSUE, context.getLocation(item), "Unreachable state in a `<selector>`");
                continue;
            }

            if (!hasStateQualifier(item)) {
                foundDefault = true;
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String ns = attr.getNamespaceURI();
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
            }
            if (name.startsWith("android:")) {
                name = name.substring(8);
            }
            if (name.startsWith("state_")) {
                if (ns == null || SdkConstants.ANDROID_URI.equals(ns)) {
                    return true;
                }
            }
        }
        return false;
    }
}