package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "StateListReachable",
        "Unreachable state in a selector",
        "In a selector, only the last child in the state list should omit a " +
        "state qualifier. If not, all subsequent items in the list will be ignored " +
        "since the given item will match all.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                items.add((Element) child);
            }
        }

        for (int i = 0; i < items.size() - 1; i++) {
            Element item = items.get(i);
            if (!hasStateQualifier(item)) {
                context.report(ISSUE, context.getLocation(item),
                    "This item is missing a state qualifier and is not the last item, " +
                    "making all subsequent items unreachable");
                break;
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attrs = item.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String ns = attr.getNamespaceURI();
            String name = attr.getLocalName();
            if (ANDROID_URI.equals(ns) && name != null && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}