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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_FILE = "AndroidManifest.xml";
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String WEAR_HARDWARE_TYPE = "android.hardware.type.watch";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWearFeatureAttribute",
                    "Invalid attribute for Wear uses-feature",
                    "The `android.hardware.type.watch` uses-feature must not use `android:required=\"false\"`. "
                            + "A single APK for Wear and non-Wear devices is not supported.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!ANDROID_MANIFEST_FILE.equals(context.file.getName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME);
        if (!WEAR_HARDWARE_TYPE.equals(name.trim())) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_REQUIRED);
        if ("false".equals(required.trim())) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`android:required=\"false\"` is not allowed for `android.hardware.type.watch`");
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }
}