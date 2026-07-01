package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";

    private boolean mFoundStandaloneFlag;
    private Element mApplicationElement;

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone app. " +
                    "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
                    "your application element and set the value to `true` or `false`.\n" +
                    "```xml\n" +
                    "<meta-data android:name=\"com.google.android.wearable.standalone\"\n" +
                    "           android:value=\"true\"/>\n" +
                    "```",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFoundStandaloneFlag = false;
        mApplicationElement = null;
    }

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
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (META_DATA_STANDALONE.equals(name)) {
                String value = element.getAttributeNS(ANDROID_URI, "value");
                if ("true".equals(value) || "false".equals(value)) {
                    mFoundStandaloneFlag = true;
                } else {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Invalid value for Wear standalone app flag. Must be \"true\" or \"false\".");
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mFoundStandaloneFlag && mApplicationElement != null && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, mApplicationElement, xmlContext.getLocation(mApplicationElement),
                    "Missing Wear standalone app flag. Add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/>` to your application element.");
        }
    }
}