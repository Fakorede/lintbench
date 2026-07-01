package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
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
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    private boolean mHasWatchFeature;
    private boolean mHasStandaloneMetadata;
    private boolean mStandaloneValueValid;
    private Element mApplicationElement;
    private Element mMetadataElement;

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasWatchFeature = false;
        mHasStandaloneMetadata = false;
        mStandaloneValueValid = false;
        mApplicationElement = null;
        mMetadataElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "meta-data", "application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.hardware.type.watch".equals(name)) {
                mHasWatchFeature = true;
            }
        } else if ("meta-data".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("com.google.android.wearable.standalone".equals(name)) {
                mHasStandaloneMetadata = true;
                mMetadataElement = element;
                String value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                if ("true".equals(value) || "false".equals(value)) {
                    mStandaloneValueValid = true;
                }
            }
        } else if ("application".equals(tagName)) {
            mApplicationElement = element;
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mHasWatchFeature) {
            if (!mHasStandaloneMetadata) {
                if (mApplicationElement != null) {
                    context.report(
                            ISSUE,
                            mApplicationElement,
                            context.getNameLocation(mApplicationElement),
                            "Missing Wear standalone app flag. Please add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/>` to your `<application>` element.");
                }
            } else if (!mStandaloneValueValid) {
                if (mMetadataElement != null) {
                    Attr valueAttr = mMetadataElement.getAttributeNodeNS(ANDROID_URI, ATTR_VALUE);
                    Location location = valueAttr != null ? context.getLocation(valueAttr) : context.getLocation(mMetadataElement);
                    context.report(
                            ISSUE,
                            mMetadataElement,
                            location,
                            "The Wear standalone app flag value must be \"true\" or \"false\"");
                }
            }
        }
    }
}