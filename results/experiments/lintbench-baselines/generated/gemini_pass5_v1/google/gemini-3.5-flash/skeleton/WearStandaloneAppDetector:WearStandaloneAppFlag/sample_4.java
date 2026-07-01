package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone app. " +
                    "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
                    "your application element and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasWatchFeature;
    private Element mApplication;
    private Element mStandaloneMetadata;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasWatchFeature = false;
        mApplication = null;
        mStandaloneMetadata = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "application");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = getAndroidAttribute(element, "name");
            if ("android.hardware.type.watch".equals(name)) {
                mHasWatchFeature = true;
            }
        } else if ("application".equals(tagName)) {
            mApplication = element;
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if ("meta-data".equals(childElement.getTagName())) {
                        String name = getAndroidAttribute(childElement, "name");
                        if ("com.google.android.wearable.standalone".equals(name)) {
                            mStandaloneMetadata = childElement;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasWatchFeature && mApplication != null && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (mStandaloneMetadata == null) {
                Location location = xmlContext.getNameLocation(mApplication);
                xmlContext.report(
                        ISSUE,
                        location,
                        "Missing Wear standalone app flag. Please add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/>` to your `<application>` element.");
            } else {
                String value = getAndroidAttribute(mStandaloneMetadata, "value");
                if (!"true".equals(value) && !"false".equals(value)) {
                    Attr valueAttr = mStandaloneMetadata.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "value");
                    Location location = valueAttr != null ? xmlContext.getLocation(valueAttr) : xmlContext.getLocation(mStandaloneMetadata);
                    xmlContext.report(
                            ISSUE,
                            location,
                            "Invalid Wear standalone app flag value. Expecting 'true' or 'false'.");
                }
            }
        }
    }

    private String getAndroidAttribute(Element element, String localName) {
        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value;
    }
}