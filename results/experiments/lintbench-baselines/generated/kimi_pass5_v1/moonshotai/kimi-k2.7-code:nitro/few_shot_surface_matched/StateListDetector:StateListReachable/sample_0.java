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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class StateListDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a <selector>",
                    "In a selector, only the last child in the state list should omit a state qualifier. "
                            + "If a default item (with no state qualifiers) appears before the end, "
                            + "all subsequent items will be ignored because the default item matches all states.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !TAG_SELECTOR.equals(root.getTagName())) {
            return;
        }

        List<Element> items = new ArrayList<>();
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && child instanceof Element) {
                Element element = (Element) child;
                if (TAG_ITEM.equals(element.getTagName())) {
                    items.add(element);
                }
            }
            child = child.getNextSibling();
        }

        int count = items.size();
        for (int i = 0; i < count; i++) {
            Element item = items.get(i);
            if (i < count - 1 && !hasStateAttribute(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item has no state qualifiers and will match all states, making later items unreachable; "
                                + "move it to the end of the selector");
            }
        }
    }

    private static boolean hasStateAttribute(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        int length = attributes.getLength();
        for (int i = 0; i < length; i++) {
            String localName = attributes.item(i).getLocalName();
            if (localName != null && localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}