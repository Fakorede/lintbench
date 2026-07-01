package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a " +
            "state qualifier. If not, all subsequent items in the list will be ignored " +
            "since the given item will match all.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    StateListDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("selector", "animated-selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        int numChildren = children.getLength();
        for (int i = 0; i < numChildren; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) child;
                String tagName = item.getTagName();
                if (tagName.equals("item") || tagName.endsWith(":item")) {
                    if (!hasStateAttributes(item)) {
                        if (!isLastItem(item)) {
                            context.report(
                                    ISSUE,
                                    item,
                                    context.getLocation(item),
                                    "This item will match all states, so any subsequent items will be ignored"
                            );
                        }
                    }
                }
            }
        }
    }

    private static boolean isLastItem(Element item) {
        Node next = item.getNextSibling();
        while (next != null) {
            if (next.getNodeType() == Node.ELEMENT_NODE) {
                String name = ((Element) next).getTagName();
                if (name.equals("item") || name.endsWith(":item")) {
                    return false;
                }
            }
            next = next.getNextSibling();
        }
        return true;
    }

    private static boolean hasStateAttributes(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getLocalName();
            if (name == null) {
                name = attribute.getNodeName();
            }
            if (name.startsWith("state_") || name.contains(":state_")) {
                return true;
            }
        }
        return false;
    }
}