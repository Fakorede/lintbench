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

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone app. "
                            + "Add a valid meta-data entry for `com.google.android.wearable.standalone` "
                            + "to your application element and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsWearApp;
    private Element mApplicationElement;
    private Element mStandaloneMetadataElement;
    private String mStandaloneValue;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsWearApp = false;
        mApplicationElement = null;
        mStandaloneMetadataElement = null;
        mStandaloneValue = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "application", "meta-data");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.hardware.type.watch".equals(name)) {
                mIsWearApp = true;
            }
        } else if ("application".equals(tagName)) {
            mApplicationElement = element;
        } else if ("meta-data".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("com.google.android.wearable.standalone".equals(name)) {
                mStandaloneMetadataElement = element;
                mStandaloneValue = element.getAttributeNS("http://schemas.android.com/apk/res/android", "value");
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        if (mIsWearApp) {
            if (mStandaloneMetadataElement == null) {
                if (mApplicationElement != null) {
                    xmlContext.report(
                            ISSUE,
                            mApplicationElement,
                            xmlContext.getNameLocation(mApplicationElement),
                            "Missing Wear standalone app flag. Please add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/>` to your `<application>` element.");
                }
            } else {
                validateValue(xmlContext);
            }
        } else if (mStandaloneMetadataElement != null) {
            validateValue(xmlContext);
        }
    }

    private void validateValue(@NonNull XmlContext context) {
        if (!"true".equals(mStandaloneValue) && !"false".equals(mStandaloneValue)) {
            Attr valueAttr = mStandaloneMetadataElement.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "value");
            Location location = valueAttr != null ? context.getValueLocation(valueAttr) : context.getLocation(mStandaloneMetadataElement);
            context.report(
                    ISSUE,
                    mStandaloneMetadataElement,
                    location,
                    "Invalid Wear standalone app flag value. Expecting 'true' or 'false'.");
        }
    }
}