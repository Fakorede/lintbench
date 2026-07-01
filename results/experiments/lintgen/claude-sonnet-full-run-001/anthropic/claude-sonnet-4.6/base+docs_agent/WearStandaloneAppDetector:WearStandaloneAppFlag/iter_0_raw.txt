package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
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
            5,
            Severity.ERROR,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    private static final String STANDALONE_META_DATA_NAME =
            "com.google.android.wearable.standalone";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only check Wear OS manifests
        if (!isWearManifest(element)) {
            return;
        }

        Element standaloneMetaData = findStandaloneMetaData(element);

        if (standaloneMetaData == null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `<meta-data android:name=\"" + STANDALONE_META_DATA_NAME + "\" " +
                    "android:value=\"true|false\"/>` element in `<application>`"
            );
            return;
        }

        // Check that the value is "true" or "false"
        Attr valueAttr = standaloneMetaData.getAttributeNodeNS(ANDROID_NS, ATTR_VALUE);
        if (valueAttr == null) {
            context.report(
                    ISSUE,
                    standaloneMetaData,
                    context.getLocation(standaloneMetaData),
                    "The `" + STANDALONE_META_DATA_NAME + "` meta-data is missing the " +
                    "`android:value` attribute; it should be set to `true` or `false`"
            );
            return;
        }

        String value = valueAttr.getValue();
        if (!"true".equals(value) && !"false".equals(value)) {
            context.report(
                    ISSUE,
                    valueAttr,
                    context.getLocation(valueAttr),
                    "The `" + STANDALONE_META_DATA_NAME + "` meta-data `android:value` must be " +
                    "set to `true` or `false`; found `" + value + "`"
            );
        }
    }

    private boolean isWearManifest(Element applicationElement) {
        Node parent = applicationElement.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }
        Element manifest = (Element) parent;

        // Check for uses-feature android.hardware.type.watch
        NodeList children = manifest.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if ("uses-feature".equals(childElement.getTagName())) {
                Attr nameAttr = childElement.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
                if (nameAttr != null &&
                        "android.hardware.type.watch".equals(nameAttr.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Element findStandaloneMetaData(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_META_DATA.equals(childElement.getTagName())) {
                Attr nameAttr = childElement.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
                if (nameAttr != null &&
                        STANDALONE_META_DATA_NAME.equals(nameAttr.getValue())) {
                    return childElement;
                }
            }
        }
        return null;
    }
}