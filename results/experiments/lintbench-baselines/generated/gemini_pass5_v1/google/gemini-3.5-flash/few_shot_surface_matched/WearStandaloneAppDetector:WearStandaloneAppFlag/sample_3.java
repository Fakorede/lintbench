package com.android.tools.lint.checks;

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
import java.util.Arrays;
import java.util.Collection;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone app. "
                            + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                            + "your application element and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private boolean hasWatchFeature;
    private boolean hasStandaloneFlag;
    private Element applicationElement;
    private Element standaloneElement;

    @Override
    public void beforeCheckFile(XmlContext context) {
        hasWatchFeature = false;
        hasStandaloneFlag = false;
        applicationElement = null;
        standaloneElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "application", "meta-data");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.hardware.type.watch".equals(name)) {
                hasWatchFeature = true;
            }
        } else if ("application".equals(tagName)) {
            applicationElement = element;
        } else if ("meta-data".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("com.google.android.wearable.standalone".equals(name)) {
                Node parent = element.getParentNode();
                if (parent instanceof Element && "application".equals(((Element) parent).getTagName())) {
                    hasStandaloneFlag = true;
                    standaloneElement = element;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (hasWatchFeature) {
            if (!hasStandaloneFlag) {
                if (applicationElement != null) {
                    context.report(
                            ISSUE,
                            applicationElement,
                            context.getLocation(applicationElement),
                            "Missing Wear standalone app flag. Please add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/>` to your `<application>` element.");
                }
            } else if (standaloneElement != null) {
                String value = standaloneElement.getAttributeNS(ANDROID_URI, "value");
                if (!"true".equals(value) && !"false".equals(value)) {
                    context.report(
                            ISSUE,
                            standaloneElement,
                            context.getLocation(standaloneElement),
                            "The Wear standalone app flag must be \"true\" or \"false\"");
                }
            }
        }
    }
}