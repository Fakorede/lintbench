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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class StateListDetector extends Detector implements XmlScanner {

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
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        int count = children.getLength();
        Element lastItem = null;

        for (int i = 0; i < count; i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String name = node.getLocalName();
                if (name == null) {
                    name = node.getNodeName();
                }
                if ("item".equals(name)) {
                    lastItem = (Element) node;
                }
            }
        }

        if (lastItem == null) {
            return;
        }

        for (int i = 0; i < count; i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String name = node.getLocalName();
                if (name == null) {
                    name = node.getNodeName();
                }
                if ("item".equals(name)) {
                    Element item = (Element) node;
                    if (item == lastItem) {
                        break;
                    }
                    if (!hasStateQualifier(item)) {
                        context.report(ISSUE, context.getLocation(item),
                                "Unreachable state: This item has no state qualifiers but is not the last item, " +
                                "so all subsequent items will be ignored");
                    }
                }
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getName();
                int colon = name.indexOf(':');
                if (colon != -1) {
                    name = name.substring(colon + 1);
                }
            }
            if (name != null && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}