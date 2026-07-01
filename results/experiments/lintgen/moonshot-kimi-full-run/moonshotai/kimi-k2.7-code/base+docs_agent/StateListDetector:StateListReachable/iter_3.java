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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        List<Element> items = new ArrayList<>();
        List<Map<String, String>> itemStates = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String localName = child.getLocalName();
            if (localName == null) {
                String nodeName = child.getNodeName();
                if (nodeName != null) {
                    int colonIndex = nodeName.indexOf(':');
                    localName = colonIndex == -1 ? nodeName : nodeName.substring(colonIndex + 1);
                }
            }

            if (!TAG_ITEM.equals(localName)) {
                continue;
            }

            Element item = (Element) child;
            items.add(item);
            itemStates.add(getStateMap(item));
        }

        for (int j = 0; j < items.size(); j++) {
            Element item = items.get(j);
            Map<String, String> states = itemStates.get(j);

            if (states.isEmpty()) {
                if (j < items.size() - 1) {
                    context.report(
                            ISSUE,
                            item,
                            context.getLocation(item),
                            "This item has no state qualifier and is not the last item, so subsequent items will be unreachable");
                }
                continue;
            }

            for (int i = 0; i < j; i++) {
                Map<String, String> earlierStates = itemStates.get(i);
                if (earlierStates.isEmpty()) {
                    continue;
                }
                if (isSubset(earlierStates, states)) {
                    context.report(
                            ISSUE,
                            item,
                            context.getLocation(item),
                            "This item is unreachable because an earlier item matches the same states");
                    break;
                }
            }
        }
    }

    private static Map<String, String> getStateMap(@NonNull Element item) {
        Map<String, String> states = new HashMap<>();
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
            if (!ANDROID_URI.equals(namespaceUri)) {
                continue;
            }

            states.put(name, attr.getNodeValue());
        }
        return states;
    }

    private static boolean isSubset(Map<String, String> earlier, Map<String, String> later) {
        for (Map.Entry<String, String> entry : earlier.entrySet()) {
            String laterValue = later.get(entry.getKey());
            if (laterValue == null || !laterValue.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
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