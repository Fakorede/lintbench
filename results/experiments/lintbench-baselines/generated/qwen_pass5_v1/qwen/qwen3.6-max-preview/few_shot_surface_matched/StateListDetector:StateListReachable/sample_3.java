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
            "Unreachable state in a selector",
            "In a selector, only the last child in the state list should omit a state qualifier. " +
            "If not, all subsequent items in the list will be ignored since the given item will match all.",
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
        boolean foundCatchAll = false;

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) child;
            if (!"item".equals(item.getTagName())) {
                continue;
            }

            if (foundCatchAll) {
                context.report(ISSUE, item, context.getLocation(item),
                        "This item is unreachable because a previous item matches all states");
                continue;
            }

            if (!hasStateQualifier(item)) {
                foundCatchAll = true;
            }
        }
    }

    private static boolean hasStateQualifier(@NonNull Element item) {
        NamedNodeMap attrs = item.getAttributes();
        if (attrs == null) {
            return false;
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String localName = attr.getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}