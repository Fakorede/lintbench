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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String STANDALONE_METADATA_NAME =
            "com.google.android.wearable.standalone";
    private static final String WATCH_FEATURE = "android.hardware.type.watch";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps must specify whether they can work without a phone app by "
                            + "adding a `<meta-data>` element for `com.google.android.wearable.standalone` "
                            + "to the `<application>` element, with a value of `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsManifest;
    private boolean mIsWearable;
    private boolean mHasStandaloneMetadata;
    private boolean mReported;
    private Element mApplicationElement;

    @Override
    public void beforeCheckFile(Context context) {
        mIsManifest = context.getFile().getName().equals(ANDROID_MANIFEST_XML);
        mIsWearable = false;
        mHasStandaloneMetadata = false;
        mReported = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!mIsManifest) {
            return;
        }

        String tag = element.getTagName();
        if ("uses-feature".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (WATCH_FEATURE.equals(name)) {
                mIsWearable = true;
            }
        } else if ("application".equals(tag)) {
            mApplicationElement = element;
            checkApplication(context, element);
        }
    }

    private void checkApplication(XmlContext context, Element application) {
        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof Element)) {
                continue;
            }
            Element metaData = (Element) child;
            if (!"meta-data".equals(metaData.getTagName())) {
                continue;
            }

            String name = metaData.getAttributeNS(ANDROID_URI, "name");
            if (!STANDALONE_METADATA_NAME.equals(name)) {
                continue;
            }

            String value = metaData.getAttributeNS(ANDROID_URI, "value");
            if (value.isEmpty()) {
                context.report(
                        ISSUE,
                        context.getLocation(metaData),
                        "The `com.google.android.wearable.standalone` meta-data must have a value of `true` or `false`.");
                mReported = true;
                mHasStandaloneMetadata = false;
            } else if (!"true".equals(value) && !"false".equals(value)) {
                context.report(
                        ISSUE,
                        context.getLocation(metaData),
                        "The `com.google.android.wearable.standalone` value must be `true` or `false`.");
                mReported = true;
                mHasStandaloneMetadata = false;
            } else {
                mHasStandaloneMetadata = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mIsManifest || !mIsWearable || mHasStandaloneMetadata || mReported) {
            return;
        }

        if (mApplicationElement != null) {
            context.report(
                    ISSUE,
                    ((XmlContext) context).getLocation(mApplicationElement),
                    "Missing `com.google.android.wearable.standalone` meta-data element in `<application>`. "
                            + "Set the value to `true` or `false` to indicate whether the app can work standalone.");
        }
    }
}