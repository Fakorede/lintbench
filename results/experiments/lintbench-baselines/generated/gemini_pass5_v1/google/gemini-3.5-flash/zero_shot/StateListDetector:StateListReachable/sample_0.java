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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR
                || folderType == ResourceFolderType.ANIMATOR;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("selector");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        List<Element> items = new ArrayList<>();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                items.add((Element) child);
            }
        }

        int size = items.size();
        for (int i = 0; i < size - 1; i++) {
            Element item = items.get(i);
            if (!hasStateQualifier(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getNameLocation(item),
                        "This item has no state qualifier and is not the last item in the list, " +
                        "so subsequent items will be ignored"
                );
            }
        }
    }

    private boolean hasStateQualifier(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getNodeName();
                int colon = localName.indexOf(':');
                if (colon != -1) {
                    localName = localName.substring(colon + 1);
                }
            }
            if (localName.startsWith("state_")) {
                return true;
            }
        }
        return false;
    }
}