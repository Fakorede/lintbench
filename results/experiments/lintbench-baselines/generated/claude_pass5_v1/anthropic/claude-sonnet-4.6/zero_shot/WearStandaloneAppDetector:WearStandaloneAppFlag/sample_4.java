package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.ResourceXmlDetector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

/**
 * Detector that checks whether a Wear app has a valid standalone app flag.
 */
public class WearStandaloneAppDetector extends ResourceXmlDetector implements XmlScanner {

    private static final String STANDALONE_META_DATA_NAME =
            "com.google.android.wearable.standalone";

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_META_DATA = "meta-data";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    private static final String WEARABLE_FEATURE =
            "android.hardware.type.watch";

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
            Severity.WARNING,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/training/wearables/apps/packaging.html");

    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element applicationElement) {
        // Check if this manifest is a Wear app by looking for the watch uses-feature
        if (!isWearApp(applicationElement)) {
            return;
        }

        // Look for the standalone meta-data entry within the application element
        NodeList children = applicationElement.getChildNodes();
        Element standaloneMetaData = null;

        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (TAG_META_DATA.equals(element.getTagName())) {
                String nameAttr = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (STANDALONE_META_DATA_NAME.equals(nameAttr)) {
                    standaloneMetaData = element;
                    break;
                }
            }
        }

        if (standaloneMetaData == null) {
            // Missing standalone meta-data entry
            context.report(
                    ISSUE,
                    applicationElement,
                    context.getLocation(applicationElement),
                    "Missing `<meta-data android:name=\"" + STANDALONE_META_DATA_NAME + "\" " +
                    "android:value=\"true|false\"/>` element in `<application>`");
        } else {
            // Check that the value is "true" or "false"
            String value = standaloneMetaData.getAttributeNS(ANDROID_NS, ATTR_VALUE);
            if (value == null || value.isEmpty()) {
                context.report(
                        ISSUE,
                        standaloneMetaData,
                        context.getLocation(standaloneMetaData),
                        "The `" + STANDALONE_META_DATA_NAME + "` meta-data is missing an " +
                        "`android:value` attribute; should be `true` or `false`");
            } else if (!"true".equals(value) && !"false".equals(value)) {
                context.report(
                        ISSUE,
                        standaloneMetaData,
                        context.getNameLocation(standaloneMetaData),
                        "The `" + STANDALONE_META_DATA_NAME + "` meta-data `android:value` " +
                        "must be `true` or `false`, but was `" + value + "`");
            }
        }
    }

    /**
     * Checks whether the manifest declares a Wear app by looking for a uses-feature element
     * with name "android.hardware.type.watch".
     */
    private boolean isWearApp(Element applicationElement) {
        // Walk up to the manifest element and search its children for uses-feature
        org.w3c.dom.Node parent = applicationElement.getParentNode();
        if (parent == null) {
            return false;
        }

        NodeList siblings = parent.getChildNodes();
        for (int i = 0; i < siblings.getLength(); i++) {
            org.w3c.dom.Node sibling = siblings.item(i);
            if (sibling.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) sibling;
            if (TAG_USES_FEATURE.equals(element.getTagName())) {
                String nameAttr = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (WEARABLE_FEATURE.equals(nameAttr)) {
                    return true;
                }
            }
        }
        return false;
    }
}