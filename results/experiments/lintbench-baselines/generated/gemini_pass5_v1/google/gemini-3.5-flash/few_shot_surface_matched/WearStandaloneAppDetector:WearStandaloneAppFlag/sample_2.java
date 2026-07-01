package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

    private static final String WEAR_STANDALONE_METADATA = "com.google.android.wearable.standalone";
    private static final String WATCH_FEATURE = "android.hardware.type.watch";

    private boolean mHasWatchFeature;
    private boolean mHasStandaloneMetadata;
    private boolean mValidStandaloneValue;
    private Element mApplicationElement;
    private Element mMetadataElement;

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mHasWatchFeature = false;
        mHasStandaloneMetadata = false;
        mValidStandaloneValue = false;
        mApplicationElement = null;
        mMetadataElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_APPLICATION,
                SdkConstants.TAG_USES_FEATURE,
                SdkConstants.TAG_META_DATA
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (SdkConstants.TAG_APPLICATION.equals(tagName)) {
            mApplicationElement = element;
        } else if (SdkConstants.TAG_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (WATCH_FEATURE.equals(name)) {
                mHasWatchFeature = true;
            }
        } else if (SdkConstants.TAG_META_DATA.equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (WEAR_STANDALONE_METADATA.equals(name)) {
                mHasStandaloneMetadata = true;
                mMetadataElement = element;
                String value = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
                if ("true".equals(value) || "false".equals(value)) {
                    mValidStandaloneValue = true;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mHasWatchFeature) {
            if (!mHasStandaloneMetadata) {
                if (mApplicationElement != null) {
                    context.report(
                            ISSUE,
                            mApplicationElement,
                            context.getLocation(mApplicationElement),
                            "Missing Wear standalone app flag. Please add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/>` to your `<application>` element."
                    );
                }
            } else if (!mValidStandaloneValue) {
                if (mMetadataElement != null) {
                    context.report(
                            ISSUE,
                            mMetadataElement,
                            context.getLocation(mMetadataElement),
                            "The Wear standalone app flag value must be either \"true\" or \"false\"."
                    );
                }
            }
        }
    }
}