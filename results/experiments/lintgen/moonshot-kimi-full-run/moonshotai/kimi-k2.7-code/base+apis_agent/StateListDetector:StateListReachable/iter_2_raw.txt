package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class StateListDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in selector",
            "In a `<selector>`, only the last child should omit a state qualifier. "
                    + "If a non-last item has no state qualifiers, it matches every state and "
                    + "all subsequent items in the list will be ignored.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ANDROID_PREFIX = "android:";
    private static final String SELECTOR = "selector";
    private static final String ITEM = "item";
    private static final String STATE_PREFIX = "state_";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.COLOR;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SELECTOR);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node child = element.getFirstChild();
        Element lastItem = null;
        int itemCount = 0;

        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && isItem(child)) {
                lastItem = (Element) child;
                itemCount++;
            }
            child = child.getNextSibling();
        }

        if (itemCount < 2 || lastItem == null) {
            return;
        }

        child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && isItem(child)) {
                Element item = (Element) child;
                if (item != lastItem && !hasStateQualifier(item)) {
                    context.report(
                            ISSUE,
                            item,
                            context.getLocation(item),
                            "This item has no state qualifiers and will match all states, "
                                    + "making subsequent items unreachable."
                    );
                }
            }
            child = child.getNextSibling();
        }
    }

    private static boolean isItem(Node node) {
        String name = node.getLocalName();
        if (name == null) {
            name = node.getNodeName();
        }
        return ITEM.equals(name);
    }

    private static boolean hasStateQualifier(Element item) {
        NamedNodeMap attributes = item.getAttributes();
        if (attributes == null) {
            return false;
        }
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            if (localName != null) {
                String namespaceUri = attr.getNamespaceURI();
                if (localName.startsWith(STATE_PREFIX)
                        && (ANDROID_URI.equals(namespaceUri) || namespaceUri == null)) {
                    return true;
                }
            } else {
                String name = attr.getName();
                if (name != null && name.startsWith(ANDROID_PREFIX + STATE_PREFIX)) {
                    return true;
                }
            }
        }
        return false;
    }
}