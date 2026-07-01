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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";

    private boolean mFoundStandaloneFlag;
    private Element mApplicationElement;

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
        if (!context.isManifestFile()) {
            return;
        }

        String tagName = element.getTagName();
        if ("application".equals(tagName)) {
            mApplicationElement = element;
        } else if ("meta-data".equals(tagName)) {
            Element parent = (Element) element.getParentNode();
            if (parent != null && "application".equals(parent.getTagName())) {
                String name = element.getAttributeNS(ANDROID_URI, "name");
                if (META_DATA_STANDALONE.equals(name)) {
                    mFoundStandaloneFlag = true;
                    String value = element.getAttributeNS(ANDROID_URI, "value");
                    if (!"true".equals(value) && !"false".equals(value)) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "The `com.google.android.wearable.standalone` meta-data value must be `true` or `false`");
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context.isManifestFile() && mApplicationElement != null && !mFoundStandaloneFlag) {
            context.report(ISSUE, mApplicationElement, context.getLocation(mApplicationElement),
                    "Missing `com.google.android.wearable.standalone` meta-data element in the application tag");
        }
    }
}