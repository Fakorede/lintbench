package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class StateListDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a <selector>",
            "In a selector, only the last child in the state list should omit a state qualifier. " +
            "If not, all subsequent items in the list will be ignored since the given item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public EnumSet<Scope> getScope() {
        return EnumSet.of(Scope.RESOURCE_FILE_SCOPE);
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean previousItemWasCatchAll = false;
        Node child = element.getFirstChild();

        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                if ("item".equals(item.getTagName())) {
                    boolean isCatchAll = isCatchAllItem(item);

                    if (previousItemWasCatchAll) {
                        context.report(ISSUE, item,
                                "This state is unreachable because a previous item matches all states.");
                    }

                    if (isCatchAll) {
                        previousItemWasCatchAll = true;
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    private boolean isCatchAllItem(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return true;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String localName = attr.getLocalName();
            String ns = attr.getNamespaceURI();

            boolean isStateAttr = false;

            if (localName != null && localName.startsWith("state_")) {
                if (ns == null || "http://schemas.android.com/apk/res/android".equals(ns)) {
                    isStateAttr = true;
                }
            }

            if (!isStateAttr) {
                String name = attr.getNodeName();
                if (name != null && name.startsWith("android:state_")) {
                    isStateAttr = true;
                }
            }

            if (isStateAttr) {
                return false;
            }
        }
        return true;
    }
}