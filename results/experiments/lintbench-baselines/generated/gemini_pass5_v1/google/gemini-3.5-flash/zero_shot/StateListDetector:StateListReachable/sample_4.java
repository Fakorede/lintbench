package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a state qualifier. " +
            "If not, all subsequent items in the list will be ignored since the given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

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
        List<Element> items = new ArrayList<>();
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = child.getLocalName();
                if (nodeName == null) {
                    nodeName = child.getNodeName();
                }
                if ("item".equals(nodeName)) {
                    items.add((Element) child);
                }
            }
            child = child.getNextSibling();
        }

        int numItems = items.size();
        for (int i = 0; i < numItems - 1; i++) {
            Element item = items.get(i);
            if (!hasStateQualifier(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getNameLocation(item),
                        "This item matches all states and is not the last item, " +
                                "so subsequent items will never be reached"
                );
                break;
            }
        }
    }

    private boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getNodeName();
                int colon = localName.indexOf(':');
                if (colon != -1) {
                    localName = localName.substring(colon + 1);
                }
            }
            if (localName.startsWith("state_")) {
                String ns = attr.getNamespaceURI();
                if (ns == null || SdkConstants.ANDROID_URI.equals(ns)) {
                    return true;
                }
            }
        }
        return false;
    }
}