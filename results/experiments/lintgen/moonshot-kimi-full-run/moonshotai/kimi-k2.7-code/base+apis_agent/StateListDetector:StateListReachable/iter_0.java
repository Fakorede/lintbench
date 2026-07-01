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
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.List;

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in selector",
            "In a `<selector>`, only the last child should omit a state qualifier. " +
            "If a non-last item has no state qualifiers, it matches every state and " +
            "all subsequent items in the list will be ignored.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String SELECTOR = "selector";
    private static final String ITEM = "item";
    private static final String STATE_PREFIX = "state_";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList(SELECTOR);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node child = element.getFirstChild();
        Element lastItem = null;
        int itemCount = 0;

        // First pass: count items and find the last item element.
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && ITEM.equals(child.getLocalName())) {
                lastItem = (Element) child;
                itemCount++;
            }
            child = child.getNextSibling();
        }

        if (itemCount < 2 || lastItem == null) {
            return;
        }

        // Second pass: report any non-last item that lacks a state qualifier.
        child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && ITEM.equals(child.getLocalName())) {
                Element item = (Element) child;
                if (item != lastItem && !hasStateQualifier(item)) {
                    context.report(ISSUE, item, context.getLocation(item),
                            "This item has no state qualifiers and will match all states, " +
                            "making subsequent items unreachable.");
                }
            }
            child = child.getNextSibling();
        }
    }

    private static boolean hasStateQualifier(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            String name = attr.getLocalName();
            if (name != null && name.startsWith(STATE_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}