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
        return Collections.singletonList(SdkConstants.TAG_SELECTOR);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        int count = children.getLength();

        for (int i = 0; i < count; i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_ITEM.equals(node.getNodeName())) {
                Element item = (Element) node;
                if (!hasStateQualifier(item)) {
                    for (int j = i + 1; j < count; j++) {
                        Node next = children.item(j);
                        if (next.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_ITEM.equals(next.getNodeName())) {
                            context.report(ISSUE, context.getLocation(item),
                                    "Unreachable state: This item matches all states and is not the last item, " +
                                    "so subsequent items will be ignored.");
                            break;
                        }
                    }
                }
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attr = attributes.item(i);
                String name = attr.getLocalName();
                if (name == null) {
                    name = attr.getNodeName();
                }
                if (name != null && (name.startsWith("state_") || name.startsWith("android:state_"))) {
                    return true;
                }
            }
        }
        return false;
    }
}