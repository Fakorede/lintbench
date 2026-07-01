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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String STANDALONE_META_DATA_NAME =
            "com.google.android.wearable.standalone";

    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_VALUE = "android:value";

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
            )
    ).addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element applicationElement) {
        // Only check AndroidManifest.xml files
        if (!context.file.getName().equals(ANDROID_MANIFEST)) {
            return;
        }

        // Look for the standalone meta-data element within the application element
        Element standaloneMetaData = null;
        NodeList children = applicationElement.getChildNodes();
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
            if (STANDALONE_META_DATA_NAME.equals(nameAttr)) {
                standaloneMetaData = childElement;
                break;
            }
        }

        if (standaloneMetaData == null) {
            // Missing the meta-data entry entirely
            context.report(
                    ISSUE,
                    applicationElement,
                    context.getLocation(applicationElement),
                    "Wearable apps should specify whether they can work standalone, without a " +
                    "phone app. Add a valid meta-data entry for " +
                    "`com.google.android.wearable.standalone` to your application element and " +
                    "set the value to `true` or `false`."
            );
        } else {
            // Check that the value is "true" or "false"
            String value = standaloneMetaData.getAttribute(ATTR_VALUE);
            if (!"true".equals(value) && !"false".equals(value)) {
                context.report(
                        ISSUE,
                        standaloneMetaData,
                        context.getLocation(standaloneMetaData),
                        "The `com.google.android.wearable.standalone` meta-data value must be " +
                        "set to `true` or `false`."
                );
            }
        }
    }
}