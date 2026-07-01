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

public class StateListDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state list item",
            "In a `<selector>`, only the last child should omit a state qualifier. "
                    + "If a non-last item has no state qualifiers it will match every "
                    + "state, causing subsequent items to be ignored.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String SELECTOR = "selector";
    private static final String ITEM = "item";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!SELECTOR.equals(element.getParentNode().getLocalName())) {
            return;
        }

        if (hasStateQualifiers(element) || isLastChildElement(element)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This item is missing state qualifiers and is not the last item, "
                        + "so all subsequent items in the selector will be unreachable"
        );
    }

    private static boolean hasStateQualifiers(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            String name = attr.getLocalName();
            if (name != null && name.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLastChildElement(@NonNull Element element) {
        Node next = element.getNextSibling();
        while (next != null) {
            if (next.getNodeType() == Node.ELEMENT_NODE) {
                return false;
            }
            next = next.getNextSibling();
        }
        return true;
    }
}