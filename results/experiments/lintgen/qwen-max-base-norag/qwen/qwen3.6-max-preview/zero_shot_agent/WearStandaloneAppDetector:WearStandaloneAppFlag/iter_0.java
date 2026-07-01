package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "WearStandaloneAppFlag",
        "Invalid or missing Wear standalone app flag",
        "Wearable apps should specify whether they can work standalone, without a phone app. " +
        "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
        "your application element and set the value to `true` or `false`.\n" +
        "```xml\n" +
        "<meta-data android:name=\"com.google.android.wearable.standalone\"\n" +
        "           android:value=\"true\"/>\n" +
        "```\n",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String META_DATA_TAG = "meta-data";
    private static final String WEAR_STANDALONE_NAME = "com.google.android.wearable.standalone";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        boolean found = false;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (META_DATA_TAG.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, "name");
                    if (WEAR_STANDALONE_NAME.equals(name)) {
                        found = true;
                        String value = childElement.getAttributeNS(ANDROID_URI, "value");
                        if (!"true".equals(value) && !"false".equals(value)) {
                            context.report(ISSUE, childElement, context.getLocation(childElement),
                                "Invalid value for Wear standalone app flag; must be `true` or `false`");
                        }
                        break;
                    }
                }
            }
        }

        if (!found) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing Wear standalone app flag; add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true|false\"/>`");
        }
    }
}