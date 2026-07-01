package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to your " +
            "application element and set the value to `true` or `false`.\n" +
            "```xml\n" +
            "<meta-data android:name=\"com.google.android.wearable.standalone\"\n" +
            "           android:value=\"true\"/>\n" +
            "```",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        Element standaloneMetaData = null;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("meta-data".equals(childElement.getNodeName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, "name");
                    if (META_DATA_STANDALONE.equals(name)) {
                        standaloneMetaData = childElement;
                        break;
                    }
                }
            }
        }

        if (standaloneMetaData == null) {
            context.report(ISSUE, context.getLocation(element),
                    "Missing Wear standalone app flag: add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true|false\"/>` to the `<application>` element");
        } else {
            String value = standaloneMetaData.getAttributeNS(ANDROID_URI, "value");
            if (!"true".equals(value) && !"false".equals(value)) {
                context.report(ISSUE, context.getLocation(standaloneMetaData),
                        "Invalid Wear standalone app flag: `android:value` must be `true` or `false`");
            }
        }
    }
}