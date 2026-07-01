package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class StateListDetector extends Detector implements XmlScanner {

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
        int count = children.getLength();
        boolean foundDefault = false;

        for (int i = 0; i < count; i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element item = (Element) node;
            if (!"item".equals(item.getTagName())) {
                continue;
            }

            if (foundDefault) {
                context.report(ISSUE, context.getLocation(item), "Unreachable state in a `<selector>`");
                continue;
            }

            boolean hasStateQualifier = false;
            if (item.hasAttributes()) {
                NamedNodeMap attrs = item.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Node attr = attrs.item(j);
                    String ns = attr.getNamespaceURI();
                    String localName = attr.getLocalName();
                    if (SdkConstants.ANDROID_URI.equals(ns) && localName != null && localName.startsWith("state_")) {
                        hasStateQualifier = true;
                        break;
                    }
                }
            }

            if (!hasStateQualifier) {
                foundDefault = true;
            }
        }
    }
}