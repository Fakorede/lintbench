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
            "Wearable apps should specify whether they can work standalone, without a phone app. "
                    + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                    + "your application element and set the value to `true` or `false`.\n"
                    + "```xml\n"
                    + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                    + "           android:value=\"true\"/>\n"
                    + "```\n",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String META_DATA_TAG = "meta-data";
    private static final String STANDALONE_ATTR_NAME = "com.google.android.wearable.standalone";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        Element metaDataElement = null;

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (META_DATA_TAG.equals(child.getTagName())) {
                    String name = child.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (STANDALONE_ATTR_NAME.equals(name)) {
                        metaDataElement = child;
                        break;
                    }
                }
            }
        }

        if (metaDataElement == null) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing Wear standalone app flag: add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\" />` (or `false`) to the `<application>` element");
        } else {
            String value = metaDataElement.getAttributeNS(ANDROID_NS, ATTR_VALUE);
            if (!"true".equals(value) && !"false".equals(value)) {
                context.report(ISSUE, metaDataElement, context.getLocation(metaDataElement),
                        "Invalid Wear standalone app flag value: must be `true` or `false`");
            }
        }
    }
}