package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a <selector>",
            "In a selector, only the last child in the state list should omit a state qualifier. " +
            "If not, all subsequent items in the list will be ignored since the given item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        List<Element> items = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "item".equals(node.getNodeName())) {
                items.add((Element) node);
            }
        }

        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            if (i < items.size() - 1 && !hasStateQualifier(item)) {
                context.report(ISSUE, context.getLocation(item),
                        "Unreachable state: this item matches all states and will prevent subsequent items from being used");
            }
        }
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (SdkConstants.ANDROID_URI.equals(attr.getNamespaceURI())) {
                String name = attr.getLocalName();
                if (name != null && name.startsWith("state_")) {
                    return true;
                }
            }
        }
        return false;
    }
}