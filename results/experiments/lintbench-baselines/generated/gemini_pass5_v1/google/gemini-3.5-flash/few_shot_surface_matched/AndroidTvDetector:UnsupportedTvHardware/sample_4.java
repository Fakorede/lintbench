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

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                            + "Any uses-feature not explicitly marked with `required=\"false\"` is "
                            + "necessary on the device to be installed on. Ensure that any features "
                            + "that might prevent it from being installed on a TV device are "
                            + "reviewed and marked as not required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final java.util.Set<String> UNSUPPORTED_FEATURES;

    static {
        java.util.Set<String> set = new java.util.HashSet<>();
        set.add("android.hardware.touchscreen");
        set.add("android.hardware.touchscreen.multitouch");
        set.add("android.hardware.touchscreen.multitouch.distinct");
        set.add("android.hardware.touchscreen.multitouch.jazzhand");
        set.add("android.hardware.telephony");
        set.add("android.hardware.telephony.cdma");
        set.add("android.hardware.telephony.gsm");
        set.add("android.hardware.camera");
        set.add("android.hardware.camera.autofocus");
        set.add("android.hardware.camera.flash");
        set.add("android.hardware.camera.front");
        set.add("android.hardware.nfc");
        set.add("android.hardware.nfc.hce");
        set.add("android.hardware.location.gps");
        set.add("android.hardware.microphone");
        set.add("android.hardware.sensor.barometer");
        set.add("android.hardware.sensor.compass");
        set.add("android.hardware.sensor.gyroscope");
        set.add("android.hardware.sensor.light");
        set.add("android.hardware.sensor.proximity");
        set.add("android.hardware.sensor.ambient_temperature");
        set.add("android.hardware.sensor.relative_humidity");
        UNSUPPORTED_FEATURES = java.util.Collections.unmodifiableSet(set);
    }

    private boolean mIsTvApp = false;
    private final java.util.List<org.w3c.dom.Element> mPendingViolations = new java.util.ArrayList<>();

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@com.android.annotations.NonNull Context context) {
        mIsTvApp = false;
        mPendingViolations.clear();
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                mIsTvApp = true;
            } else if (UNSUPPORTED_FEATURES.contains(name)) {
                String required = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "required");
                if (!"false".equalsIgnoreCase(required)) {
                    mPendingViolations.add(element);
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@com.android.annotations.NonNull Context context) {
        if (mIsTvApp && !mPendingViolations.isEmpty()) {
            for (org.w3c.dom.Element element : mPendingViolations) {
                String featureName = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name");
                String message = String.format(
                        "The hardware feature `%1$s` is not supported on Android TV. "
                        + "To make your app available on Android TV, you must set "
                        + "`android:required=\"false\"`.",
                        featureName);
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(ISSUE, element, xmlContext.getLocation(element), message);
            }
        }
    }
}