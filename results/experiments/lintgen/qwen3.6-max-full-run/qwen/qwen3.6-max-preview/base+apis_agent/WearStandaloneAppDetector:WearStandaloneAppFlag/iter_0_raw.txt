package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        NodeList children = element.getChildNodes();
        Element standaloneMeta = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (SdkConstants.TAG_META_DATA.equals(child.getTagName())) {
                    String name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (META_DATA_STANDALONE.equals(name)) {
                        standaloneMeta = child;
                        break;
                    }
                }
            }
        }

        if (standaloneMeta == null) {
            context.report(ISSUE, context.getLocation(element),
                    "Missing Wear standalone app flag: add <meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true|false\"/>");
            return;
        }

        String value = standaloneMeta.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
        if (!"true".equals(value) && !"false".equals(value)) {
            context.report(ISSUE, context.getLocation(standaloneMeta),
                    "Invalid Wear standalone app flag: value must be \"true\" or \"false\"");
        }
    }
}