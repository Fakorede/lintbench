package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ANDROID_PREFIX = "android:";
    private static final String META_DATA_NAME = "com.google.android.wearable.standalone";

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. "
                    + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to the "
                    + "application element and set the value to `true` or `false`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean found = false;
        boolean valid = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            if (!TAG_META_DATA.equals(child.getTagName())) {
                continue;
            }

            String name = getAndroidAttributeValue(child, "name");
            if (!META_DATA_NAME.equals(name)) {
                continue;
            }

            found = true;
            String value = getAndroidAttributeValue(child, "value");
            if ("true".equals(value) || "false".equals(value)) {
                valid = true;
            }
            break;
        }

        if (!found) {
            context.report(ISSUE, element, context.getElementLocation(element),
                    "Wear apps must declare a `<meta-data android:name=\""
                            + META_DATA_NAME
                            + "\" android:value=\"true|false\" />` element in the application tag");
        } else if (!valid) {
            context.report(ISSUE, element, context.getElementLocation(element),
                    "The `<meta-data android:name=\""
                            + META_DATA_NAME
                            + "\" />` value must be either `true` or `false`");
        }
    }

    private static String getAndroidAttributeValue(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute(ANDROID_PREFIX + localName);
        }
        return value;
    }
}