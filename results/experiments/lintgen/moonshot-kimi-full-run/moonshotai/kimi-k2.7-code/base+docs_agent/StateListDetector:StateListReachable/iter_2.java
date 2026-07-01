package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class StateListDetector extends Detector implements XmlScanner {

    private static final String TAG_SELECTOR = "selector";
    private static final String TAG_ITEM = "item";
    private static final String STATE_PREFIX = "state_";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "StateListReachable",
            "Unreachable state in a selector",
            "Only the last item in a `<selector>` should omit a state qualifier. "
                    + "If an earlier item has no state qualifier, it will match every state, "
                    + "making all subsequent items unreachable.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(StateListDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SELECTOR);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();

        int lastItemIndex = -1;
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getLocalName())) {
                lastItemIndex = i;
                break;
            }
        }

        if (lastItemIndex == -1) {
            return;
        }

        for (int i = 0; i < lastItemIndex; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !TAG_ITEM.equals(child.getLocalName())) {
                continue;
            }

            Element item = (Element) child;
            if (!hasStateQualifier(item)) {
                context.report(
                        ISSUE,
                        item,
                        context.getLocation(item),
                        "This item has no state qualifier and is not the last item, so subsequent items will be unreachable");
            }
        }
    }

    private static boolean hasStateQualifier(@NonNull Element item) {
        NamedNodeMap attributes = item.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeType() != Node.ATTRIBUTE_NODE) {
                continue;
            }

            String name = getAttributeName(attr);
            if (name == null || !name.startsWith(STATE_PREFIX)) {
                continue;
            }

            String namespaceUri = attr.getNamespaceURI();
            if (ANDROID_URI.equals(namespaceUri)) {
                return true;
            }
        }
        return false;
    }

    private static String getAttributeName(@NonNull Node attr) {
        String localName = attr.getLocalName();
        if (localName != null) {
            return localName;
        }

        String nodeName = attr.getNodeName();
        if (nodeName == null) {
            return null;
        }

        int colonIndex = nodeName.indexOf(':');
        return colonIndex == -1 ? nodeName : nodeName.substring(colonIndex + 1);
    }
}