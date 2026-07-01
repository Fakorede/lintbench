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

    private static final Implementation IMPLEMENTATION =
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "StateListReachable",
                    "Unreachable state in a `<selector>`",
                    "In a selector, only the last child in the state list should omit a "
                            + "state qualifier. If not, all subsequent items in the list will be "
                            + "ignored since the given item will match all.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String TAG_SELECTOR = "selector";
    private static final String TAG_ITEM = "item";
    private static final String ANDROID_STATE_PREFIX = "android:state_";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        if (!TAG_SELECTOR.equals(root.getTagName())) {
            return;
        }

        checkSelector(context, root);
    }

    private void checkSelector(@NonNull XmlContext context, @NonNull Element selector) {
        NodeList children = selector.getChildNodes();
        boolean foundUnconditionalItem = false;
        Element unconditionalItem = null;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_ITEM.equals(element.getTagName())) {
                continue;
            }

            if (foundUnconditionalItem) {
                // This item comes after an unconditional item — it is unreachable
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "This item is unreachable because a previous item (item #"
                                + getItemIndex(selector, unconditionalItem)
                                + ") is not constrained by any state and therefore matches"
                                + " everything");
            }

            if (!hasStateAttribute(element)) {
                foundUnconditionalItem = true;
                unconditionalItem = element;
            }
        }
    }

    /**
     * Returns true if the element has at least one android:state_* attribute.
     */
    private static boolean hasStateAttribute(@NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeName().startsWith(ANDROID_STATE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the 1-based index of the given item element among the item children of the selector.
     */
    private static int getItemIndex(@NonNull Element selector, @NonNull Element item) {
        NodeList children = selector.getChildNodes();
        int index = 0;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_ITEM.equals(element.getTagName())) {
                continue;
            }
            index++;
            if (element == item) {
                return index;
            }
        }
        return index;
    }
}