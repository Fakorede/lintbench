package com.android.tools.lint.checks;

import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_SELECTOR;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class StateListDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
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
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SELECTOR);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        int itemCount = 0;
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getNodeName())) {
                itemCount++;
            }
            child = child.getNextSibling();
        }

        child = element.getFirstChild();
        int currentItemIndex = 0;
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getNodeName())) {
                currentItemIndex++;
                if (currentItemIndex < itemCount) {
                    Element item = (Element) child;
                    if (hasNoStateQualifiers(item)) {
                        context.report(
                                ISSUE,
                                item,
                                context.getLocation(item),
                                "This item is not the last state in the selector and has no "
                                        + "state qualifiers, so all subsequent items will be "
                                        + "unreachable");
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    private static boolean hasNoStateQualifiers(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
            }
            if (name != null && name.startsWith("state_")) {
                return false;
            }
        }
        return true;
    }
}