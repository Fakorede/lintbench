package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";

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

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean found = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (SdkConstants.TAG_META_DATA.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (META_DATA_STANDALONE.equals(name)) {
                        found = true;
                        String value = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
                        if (!"true".equals(value) && !"false".equals(value)) {
                            Attr valueAttr = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
                            Location location = valueAttr != null ? context.getLocation(valueAttr) : context.getLocation(childElement);
                            context.report(ISSUE, location,
                                    "The `com.google.android.wearable.standalone` meta-data value must be `true` or `false`");
                        }
                        break;
                    }
                }
            }
        }

        if (!found) {
            context.report(ISSUE, context.getLocation(element),
                    "Missing `com.google.android.wearable.standalone` meta-data element in application");
        }
    }
}