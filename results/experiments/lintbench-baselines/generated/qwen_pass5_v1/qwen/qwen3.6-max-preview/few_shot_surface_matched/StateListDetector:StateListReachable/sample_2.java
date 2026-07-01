package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a <selector>",
            "In a selector, only the last child in the state list should omit a " +
            "state qualifier. If not, all subsequent items in the list will be ignored " +
            "since the given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        NodeList children = root.getChildNodes();
        int lastItemIndex = -1;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                lastItemIndex = i;
            }
        }

        if (lastItemIndex <= 0) {
            return;
        }

        for (int i = 0; i < lastItemIndex; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"item".equals(child.getNodeName())) {
                continue;
            }

            boolean hasStateQualifier = false;
            NamedNodeMap attrs = child.getAttributes();
            if (attrs != null) {
                for (int j = 0; j < attrs.getLength(); j++) {
                    Node attr = attrs.item(j);
                    String ns = attr.getNamespaceURI();
                    String name = attr.getLocalName();
                    if (name != null && name.startsWith("state_") &&
                        "http://schemas.android.com/apk/res/android".equals(ns)) {
                        hasStateQualifier = true;
                        break;
                    }
                }
            }

            if (!hasStateQualifier) {
                context.report(ISSUE, child, context.getLocation((Element) child),
                        "This item matches all states and will make subsequent items unreachable. " +
                        "Only the last item should omit state qualifiers.");
                break;
            }
        }
    }
}