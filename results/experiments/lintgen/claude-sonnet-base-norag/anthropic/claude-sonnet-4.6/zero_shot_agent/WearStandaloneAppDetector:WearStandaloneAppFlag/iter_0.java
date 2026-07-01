package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Detector;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_META_DATA;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String WEAR_STANDALONE_META_DATA =
            "com.google.android.wearable.standalone";

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
            "your application element and set the value to `true` or `false`.\n" +
            "```xml\n" +
            "<meta-data android:name=\"com.google.android.wearable.standalone\"\n" +
            "           android:value=\"true\"/>\n" +
            "```",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            ))
            .addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Look for the standalone meta-data entry within the application element
        Element standaloneMetaData = null;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_META_DATA.equals(childElement.getTagName())) {
                continue;
            }
            String nameAttr = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
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
                    "Missing `<meta-data android:name=\"" + WEAR_STANDALONE_META_DATA + "\" " +
                    "android:value=\"true\"/>` element in `<application>`"
            );
            return;
        }

        // Check that the value attribute is present and is "true" or "false"
        String value = standaloneMetaData.getAttributeNS(ANDROID_URI, ATTR_VALUE);
        if (value == null || value.isEmpty()) {
            // Missing value attribute
            context.report(
                    ISSUE,
                    standaloneMetaData,
                    context.getLocation(standaloneMetaData),
                    "The `" + WEAR_STANDALONE_META_DATA + "` meta-data is missing the " +
                    "`android:value` attribute; it should be set to `true` or `false`"
            );
        } else if (!"true".equals(value) && !"false".equals(value)) {
            // Invalid value
            Attr valueAttr = standaloneMetaData.getAttributeNodeNS(ANDROID_URI, ATTR_VALUE);
            context.report(
                    ISSUE,
                    standaloneMetaData,
                    valueAttr != null
                            ? context.getLocation(valueAttr)
                            : context.getLocation(standaloneMetaData),
                    "The `" + WEAR_STANDALONE_META_DATA + "` meta-data `android:value` must be " +
                    "set to `true` or `false`; found `" + value + "`"
            );
        }
    }
}