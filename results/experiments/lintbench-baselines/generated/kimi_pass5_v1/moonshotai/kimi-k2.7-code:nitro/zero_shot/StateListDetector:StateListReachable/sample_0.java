package com.android.tools.lint.checks;

import static com.android.SdkConstants.SELECTOR;

import com.android.annotations.NonNull;
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
import java.util.EnumSet;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends ResourceXmlDetector {

    private static final String ITEM_TAG = "item";
    private static final String STATE_ATTR_PREFIX = "state_";

    private static final Implementation IMPLEMENTATION = new Implementation(
            StateListDetector.class,
            EnumSet.of(Scope.RESOURCE_FILE_SCOPE));

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a `<selector>`",
            "In a selector, only the last child in the state list should omit a state qualifier. "
                    + "If not, all subsequent items in the list will be ignored since the given "
                    + "item will match all.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SELECTOR);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        List<Element> items = new ArrayList<>();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && ITEM_TAG.equals(child.getNodeName())) {
                items.add((Element) child);
            }
        }

        for (int i = 0, n = items.size() - 1; i < n; i++) {
            Element item = items.get(i);
            if (!hasStateQualifier(item)) {
                context.report(ISSUE, item, context.getLocation(item),
                        "This item has no state qualifiers and will match all states; "
                                + "subsequent items will be ignored");
            }
        }
    }

    private static boolean hasStateQualifier(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getLocalName();
            if (name == null) {
                name = attribute.getNodeName();
            }
            if (name != null && name.startsWith(STATE_ATTR_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}