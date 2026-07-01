package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
                            + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                            + "your application element and set the value to `true` or `false`.\n"
                            + "```xml\n"
                            + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                            + "           android:value=\"true\"/>\n"
                            + "```",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String META_DATA_NAME = "com.google.android.wearable.standalone";

    private XmlContext mContext;
    private Element mApplicationElement;
    private boolean mHasValidFlag;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mContext = (XmlContext) context;
        mApplicationElement = null;
        mHasValidFlag = false;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "meta-data");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            mApplicationElement = element;
        } else if ("meta-data".equals(tag)) {
            if (mApplicationElement != null && element.getParentNode() == mApplicationElement) {
                String name = element.getAttributeNS(ANDROID_NS, "name");
                if (META_DATA_NAME.equals(name)) {
                    String value = element.getAttributeNS(ANDROID_NS, "value");
                    if ("true".equals(value) || "false".equals(value)) {
                        mHasValidFlag = true;
                    } else {
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                "The value for `com.google.android.wearable.standalone` must be `true` or `false`");
                        mHasValidFlag = true;
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mApplicationElement != null && !mHasValidFlag) {
            mContext.report(
                    ISSUE,
                    mApplicationElement,
                    mContext.getLocation(mApplicationElement),
                    "Missing required meta-data `com.google.android.wearable.standalone`. "
                            + "Add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/>` "
                            + "or `false` to the application element.");
        }
    }
}