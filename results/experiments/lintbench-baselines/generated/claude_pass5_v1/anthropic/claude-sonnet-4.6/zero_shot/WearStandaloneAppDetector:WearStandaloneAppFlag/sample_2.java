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

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.TAG_APPLICATION;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue WEAR_STANDALONE_APP_ISSUE = Issue.create(
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

    private static final String WEARABLE_STANDALONE_META_DATA =
            "com.google.android.wearable.standalone";

    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only check manifests that look like Wear OS app manifests.
        // We check all application elements for the standalone meta-data.
        NodeList children = element.getChildNodes();
        Element standaloneMetaData = null;

        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_META_DATA.equals(childElement.getTagName())) {
                continue;
            }
            String nameAttr = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WEARABLE_STANDALONE_META_DATA.equals(nameAttr)) {
                standaloneMetaData = childElement;
                break;
            }
        }

        if (standaloneMetaData == null) {
            // Missing the meta-data element entirely
            context.report(
                    WEAR_STANDALONE_APP_ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `<meta-data android:name=\"" + WEARABLE_STANDALONE_META_DATA +
                    "\" android:value=\"true/false\"/>` element"
            );
        } else {
            // Check that the value is either "true" or "false"
            String value = standaloneMetaData.getAttributeNS(ANDROID_URI, ATTR_VALUE);
            if (!"true".equals(value) && !"false".equals(value)) {
                context.report(
                        WEAR_STANDALONE_APP_ISSUE,
                        standaloneMetaData,
                        context.getLocation(standaloneMetaData),
                        "The `" + WEARABLE_STANDALONE_META_DATA +
                        "` meta-data value must be set to `true` or `false`"
                );
            }
        }
    }
}