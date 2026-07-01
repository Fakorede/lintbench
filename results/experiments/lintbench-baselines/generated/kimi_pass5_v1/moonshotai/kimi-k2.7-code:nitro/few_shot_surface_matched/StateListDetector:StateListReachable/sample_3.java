package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
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

public class StateListDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a `<selector>`",
                    "In a selector, only the last child in the state list should omit a state qualifier. "
                            + "If not, all subsequent items in the list will be ignored since the given "
                            + "item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType, @NonNull String fileName) {
        return folderType == ResourceFolderType.DRAWABLE && fileName.endsWith(".xml");
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !"selector".equals(root.getTagName())) {
            return;
        }

        NodeList children = root.getChildNodes();
        int childCount = children.getLength();
        for (int i = 0; i < childCount; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"item".equals(child.getNodeName())) {
                continue;
            }

            if (!hasStateQualifier(child) && i != childCount - 1) {
                context.report(
                        ISSUE,
                        child,
                        context.getLocation(child),
                        "This item is not the last state in the list and has no state qualifier, "
                                + "so all subsequent items will be unreachable");
                return;
            }
        }
    }

    private static boolean hasStateQualifier(@NonNull Node item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }

        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                String fullName = attr.getName();
                int colon = fullName.indexOf(':');
                name = colon != -1 ? fullName.substring(colon + 1) : fullName;
            }
            if (name.startsWith("state_")) {
                return true;
            }
        }

        return false;
    }
}