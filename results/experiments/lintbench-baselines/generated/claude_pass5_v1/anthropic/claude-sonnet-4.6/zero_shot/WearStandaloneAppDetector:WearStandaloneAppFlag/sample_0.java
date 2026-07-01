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

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String STANDALONE_META_DATA_NAME =
            "com.google.android.wearable.standalone";

    private static final String TAG_META_DATA = "meta-data";

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
            Severity.WARNING,
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
        // Look for meta-data children with the standalone name
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
            String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (STANDALONE_META_DATA_NAME.equals(name)) {
                standaloneMetaData = childElement;
                break;
            }
        }

        if (standaloneMetaData == null) {
            // No standalone meta-data found at all
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `<meta-data android:name=\"" + STANDALONE_META_DATA_NAME + "\" " +
                    "android:value=\"true\"/>` element in `<application>`"
            );
            return;
        }

        // Found the meta-data element; check that value is "true" or "false"
        String value = standaloneMetaData.getAttributeNS(ANDROID_URI, ATTR_VALUE);
        if (value == null || value.isEmpty()) {
            // Missing value attribute entirely
            context.report(
                    ISSUE,
                    standaloneMetaData,
                    context.getNameLocation(standaloneMetaData),
                    "The `" + STANDALONE_META_DATA_NAME + "` meta-data is missing the " +
                    "`android:value` attribute; it should be `true` or `false`"
            );
        } else if (!"true".equals(value) && !"false".equals(value)) {
            // Value is present but not true/false
            Attr valueAttr = standaloneMetaData.getAttributeNodeNS(ANDROID_URI, ATTR_VALUE);
            context.report(
                    ISSUE,
                    standaloneMetaData,
                    valueAttr != null
                            ? context.getValueLocation(valueAttr)
                            : context.getNameLocation(standaloneMetaData),
                    "The `" + STANDALONE_META_DATA_NAME + "` meta-data `android:value` must be " +
                    "`true` or `false`, but was `" + value + "`"
            );
        }
    }
}