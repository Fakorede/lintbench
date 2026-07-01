package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

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
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mIsWearApp;
    private boolean mHasStandaloneMetadata;
    private boolean mValidStandaloneValue;
    private Element mApplicationElement;
    private Element mMetadataElement;

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mIsWearApp = false;
        mHasStandaloneMetadata = false;
        mValidStandaloneValue = false;
        mApplicationElement = null;
        mMetadataElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "application", "meta-data");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name");
            if ("android.hardware.type.watch".equals(name)) {
                mIsWearApp = true;
            }
        } else if ("application".equals(tagName)) {
            mApplicationElement = element;
        } else if ("meta-data".equals(tagName)) {
            String name = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name");
            if ("com.google.android.wearable.standalone".equals(name)) {
                mHasStandaloneMetadata = true;
                mMetadataElement = element;
                String value = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "value");
                if ("true".equals(value) || "false".equals(value)) {
                    mValidStandaloneValue = true;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mIsWearApp) {
            if (!mHasStandaloneMetadata) {
                if (mApplicationElement != null) {
                    context.report(
                            ISSUE,
                            mApplicationElement,
                            context.getNameLocation(mApplicationElement),
                            "Missing Wear standalone app flag. Wearable apps should specify whether they "
                                    + "can work standalone, without a phone app. Add a `<meta-data>` entry for "
                                    + "`com.google.android.wearable.standalone` with value `true` or `false` to "
                                    + "your `<application>` element.");
                }
            } else if (!mValidStandaloneValue) {
                if (mMetadataElement != null) {
                    context.report(
                            ISSUE,
                            mMetadataElement,
                            context.getLocation(mMetadataElement),
                            "Invalid Wear standalone app flag value. The `android:value` attribute must be 'true' or 'false'.");
                }
            }
        }
    }
}