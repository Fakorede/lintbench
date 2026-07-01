package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StateListDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
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
        List<Element> items = new ArrayList<>();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                items.add((Element) child);
            }
        }

        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            boolean hasState = false;
            NamedNodeMap attrs = item.getAttributes();
            if (attrs != null) {
                for (int j = 0; j < attrs.getLength(); j++) {
                    Node attrNode = attrs.item(j);
                    if (attrNode instanceof Attr) {
                        Attr attr = (Attr) attrNode;
                        String ns = attr.getNamespaceURI();
                        String name = attr.getLocalName();
                        if (name != null && name.startsWith("state_") && SdkConstants.ANDROID_URI.equals(ns)) {
                            hasState = true;
                            break;
                        }
                    }
                }
            }
            if (!hasState && i < items.size() - 1) {
                context.report(ISSUE, item, context.getLocation(item),
                        "This item matches all states and is not last, so subsequent items will be ignored");
            }
        }
    }
}