package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
                    "Wearable apps should specify whether they can work standalone, "
                            + "without a phone app. Add a valid meta-data entry for "
                            + "`com.google.android.wearable.standalone` to your "
                            + "application element and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";
    private static final String WEAR_STANDALONE_METADATA_NAME = "com.google.android.wearable.standalone";
    private static final String WATCH_FEATURE_NAME = "android.hardware.type.watch";

    private boolean mHasWatchFeature;
    private boolean mHasStandaloneMetadata;
    private boolean mMetadataValueValid;
    private Element mApplicationElement;
    private Element mMetadataElement;

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mHasWatchFeature = false;
        mHasStandaloneMetadata = false;
        mMetadataValueValid = false;
        mApplicationElement = null;
        mMetadataElement = null;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "uses-feature", "meta-data");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WATCH_FEATURE_NAME.equals(name)) {
                mHasWatchFeature = true;
            }
        } else if ("application".equals(tagName)) {
            mApplicationElement = element;
        } else if ("meta-data".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WEAR_STANDALONE_METADATA_NAME.equals(name)) {
                mHasStandaloneMetadata = true;
                mMetadataElement = element;
                String value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                if ("true".equals(value) || "false".equals(value)) {
                    mMetadataValueValid = true;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mHasWatchFeature) {
            if (!mHasStandaloneMetadata) {
                Element target = mApplicationElement != null ? mApplicationElement : context.document.getDocumentElement();
                if (target != null) {
                    context.report(
                            ISSUE,
                            target,
                            context.getLocation(target),
                            "Missing `<meta-data android:name=\"com.google.android.wearable.standalone\" ../>` element"
                    );
                }
            } else if (!mMetadataValueValid) {
                Element target = mMetadataElement;
                if (target != null) {
                    context.report(
                            ISSUE,
                            target,
                            context.getLocation(target),
                            "The attribute `android:value` must be a boolean (\"true\" or \"false\")"
                    );
                }
            }
        }
    }
}