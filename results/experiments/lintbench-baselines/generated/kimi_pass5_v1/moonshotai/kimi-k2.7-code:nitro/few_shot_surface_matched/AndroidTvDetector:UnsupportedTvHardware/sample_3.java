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
                    "The `<uses-feature>` element should not require this unsupported TV hardware"
                            + " feature. Any `<uses-feature>` element not explicitly marked with"
                            + " `required=\\\"false\\\"` must be present on the device for the app"
                            + " to be installed. Ensure that any features that might prevent it"
                            + " from being installed on a TV device are reviewed and marked as"
                            + " not required in the manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final java.util.Set<String> UNSUPPORTED_FEATURES;
    static {
        java.util.Set<String> set = new java.util.HashSet<>();
        set.add("android.hardware.telephony");
        set.add("android.hardware.touchscreen");
        set.add("android.hardware.faketouch");
        set.add("android.hardware.camera");
        set.add("android.hardware.camera.autofocus");
        set.add("android.hardware.camera.flash");
        set.add("android.hardware.microphone");
        set.add("android.hardware.screen.portrait");
        set.add("android.hardware.sensor.compass");
        set.add("android.hardware.sensor.gyroscope");
        UNSUPPORTED_FEATURES = java.util.Collections.unmodifiableSet(set);
    }

    private final java.util.List<org.w3c.dom.Element> mUnsupportedFeatures =
            new java.util.ArrayList<>();

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mUnsupportedFeatures.clear();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttribute("android:name");
        if (name.isEmpty() || !UNSUPPORTED_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttribute("android:required");
        if ("false".equalsIgnoreCase(required)) {
            return;
        }

        mUnsupportedFeatures.add(element);
    }

    @Override
    public void afterCheckFile(Context context) {
        for (org.w3c.dom.Element element : mUnsupportedFeatures) {
            String name = element.getAttribute("android:name");
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The hardware feature \""
                            + name
                            + "\" is not supported on Android TV and will prevent installation"
                            + " on TV devices unless required=\\\"false\\\" is specified");
        }
        mUnsupportedFeatures.clear();
    }
}