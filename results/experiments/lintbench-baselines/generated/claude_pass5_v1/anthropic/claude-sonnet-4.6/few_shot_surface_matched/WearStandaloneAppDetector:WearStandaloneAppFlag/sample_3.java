package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_META_DATA;
import static com.android.xml.AndroidManifest.NODE_USES_SDK;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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

    private static final String WEARABLE_STANDALONE = "com.google.android.wearable.standalone";

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a"
                            + " phone app. Add a valid meta-data entry for"
                            + " `com.google.android.wearable.standalone` to your application"
                            + " element and set the value to `true` or `false`.\n"
                            + "```xml\n"
                            + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                            + "           android:value=\"true\"/>\n"
                            + "```",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mFoundApplication;
    private boolean mFoundStandaloneFlag;
    private Element mApplicationElement;

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mFoundApplication = false;
        mFoundStandaloneFlag = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_META_DATA);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            mFoundApplication = true;
            mApplicationElement = element;
        } else if (TAG_META_DATA.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WEARABLE_STANDALONE.equals(name)) {
                String value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                if ("true".equals(value) || "false".equals(value)) {
                    mFoundStandaloneFlag = true;
                } else {
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "The `com.google.android.wearable.standalone` meta-data value must"
                                    + " be set to `true` or `false`");
                    mFoundStandaloneFlag = true; // already reported, don't report again
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mFoundApplication && !mFoundStandaloneFlag) {
            // Only report for Wear apps — but since the spec says to always check,
            // we report on the application element.
            if (mApplicationElement != null && context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        mApplicationElement,
                        xmlContext.getLocation(mApplicationElement),
                        "Wearable apps should specify whether they can work standalone,"
                                + " without a phone app, by setting"
                                + " `com.google.android.wearable.standalone` to `true` or"
                                + " `false` in a `<meta-data>` element in the"
                                + " `<application>` element");
            }
        }
    }
}