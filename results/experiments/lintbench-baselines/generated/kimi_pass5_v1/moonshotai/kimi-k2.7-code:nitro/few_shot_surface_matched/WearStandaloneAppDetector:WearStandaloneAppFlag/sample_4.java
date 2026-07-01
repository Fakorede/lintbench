package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_METADATA;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps must specify whether they can work standalone, without a phone"
                            + " app. Add a valid <meta-data"
                            + " android:name=\"com.google.android.wearable.standalone\""
                            + " android:value=\"true\"/> (or \"false\") to the <application>"
                            + " element.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String STANDALONE_FLAG = "com.google.android.wearable.standalone";
    private static final String WEARABLE_FEATURE = "android.hardware.type.watch";

    private boolean mIsWearableApp;
    private boolean mHasValidStandaloneFlag;
    private Element mApplicationElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsWearableApp = false;
        mHasValidStandaloneFlag = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_METADATA, NODE_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        } else if (TAG_METADATA.equals(tag)
                && mApplicationElement != null
                && element.getParentNode() == mApplicationElement) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (STANDALONE_FLAG.equals(name)) {
                String value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                if ("true".equals(value) || "false".equals(value)) {
                    mHasValidStandaloneFlag = true;
                }
            }
        } else if (NODE_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WEARABLE_FEATURE.equals(name)) {
                mIsWearableApp = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsWearableApp && mApplicationElement != null && !mHasValidStandaloneFlag) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Wearable apps must specify a valid"
                            + " `com.google.android.wearable.standalone` meta-data flag with"
                            + " value \"true\" or \"false\" in the application element");
        }
    }
}