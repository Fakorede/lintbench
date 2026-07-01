package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

/**
 * Detector that checks for the presence and validity of the Wear standalone app flag
 * in the Android manifest.
 */
public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_VALUE = "android:value";
    private static final String WEAR_STANDALONE_META_DATA =
            "com.google.android.wearable.standalone";
    private static final String USES_FEATURE_TAG = "uses-feature";
    private static final String HARDWARE_TYPE_WATCH = "android.hardware.type.watch";

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
            5,
            Severity.WARNING,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    /** Constructs a new {@link WearStandaloneAppDetector} */
    public WearStandaloneAppDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only check AndroidManifest.xml files
        if (!context.file.getName().equals(ANDROID_MANIFEST_XML)) {
            return;
        }

        // Check if this manifest declares a watch hardware feature
        if (!isWearApp(element)) {
            return;
        }

        // Look for the standalone meta-data entry in the application element
        NodeList children = element.getChildNodes();
        Element standaloneMetaData = null;

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_META_DATA.equals(childElement.getTagName())) {
                continue;
            }

            String nameAttr = childElement.getAttribute(ATTR_NAME);
            if (WEAR_STANDALONE_META_DATA.equals(nameAttr)) {
                standaloneMetaData = childElement;
                break;
            }
        }

        if (standaloneMetaData == null) {
            // Missing the meta-data entry entirely
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `<meta-data android:name=\"com.google.android.wearable.standalone\" "
                            + "android:value=\"true\"/>` element in `<application>`"
            );
        } else {
            // Check that the value is "true" or "false"
            String value = standaloneMetaData.getAttribute(ATTR_VALUE);
            if (!"true".equals(value) && !"false".equals(value)) {
                Attr valueAttr = standaloneMetaData.getAttributeNode(ATTR_VALUE);
                if (valueAttr != null) {
                    context.report(
                            ISSUE,
                            standaloneMetaData,
                            context.getLocation(valueAttr),
                            "The `com.google.android.wearable.standalone` meta-data value must "
                                    + "be set to `true` or `false`"
                    );
                } else {
                    context.report(
                            ISSUE,
                            standaloneMetaData,
                            context.getLocation(standaloneMetaData),
                            "The `com.google.android.wearable.standalone` meta-data must have "
                                    + "`android:value` set to `true` or `false`"
                    );
                }
            }
        }
    }

    /**
     * Checks whether the manifest's root element contains a uses-feature element
     * for android.hardware.type.watch, indicating this is a Wear app.
     */
    private static boolean isWearApp(Element applicationElement) {
        Node parent = applicationElement.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }

        Element manifestElement = (Element) parent;
        NodeList children = manifestElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (USES_FEATURE_TAG.equals(childElement.getTagName())) {
                String name = childElement.getAttribute(ATTR_NAME);
                if (HARDWARE_TYPE_WATCH.equals(name)) {
                    return true;
                }
            }
        }

        return false;
    }
}