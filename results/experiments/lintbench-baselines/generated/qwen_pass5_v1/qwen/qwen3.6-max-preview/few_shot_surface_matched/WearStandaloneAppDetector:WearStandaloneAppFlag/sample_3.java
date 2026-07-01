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

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";

    public static final Issue ISSUE =
            Issue.create(
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
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private boolean foundStandaloneFlag;
    private Element applicationElement;

    @Override
    public void beforeCheckFile(Context context) {
        foundStandaloneFlag = false;
        applicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "meta-data");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            applicationElement = element;
        } else if ("meta-data".equals(tag)) {
            Node parent = element.getParentNode();
            if (parent != null && "application".equals(parent.getNodeName())) {
                String name = element.getAttributeNS(ANDROID_URI, "name");
                if (META_DATA_STANDALONE.equals(name)) {
                    String value = element.getAttributeNS(ANDROID_URI, "value");
                    if ("true".equals(value) || "false".equals(value)) {
                        foundStandaloneFlag = true;
                    } else {
                        context.report(ISSUE, element, context.getLocation(element),
                                "Invalid value for wearable standalone flag. Must be \"true\" or \"false\".");
                        foundStandaloneFlag = true;
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlContext.isManifest() && applicationElement != null && !foundStandaloneFlag) {
                xmlContext.report(ISSUE, applicationElement, xmlContext.getLocation(applicationElement),
                        "Missing wearable standalone flag. Add a meta-data element with "
                                + "android:name=\"com.google.android.wearable.standalone\" and "
                                + "android:value=\"true\" or \"false\".");
            }
        }
    }
}