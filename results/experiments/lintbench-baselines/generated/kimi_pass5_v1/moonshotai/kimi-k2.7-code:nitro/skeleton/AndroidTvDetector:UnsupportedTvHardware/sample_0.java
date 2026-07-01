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
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_FEATURE = "uses-feature";

    private static final Set<String> UNSUPPORTED_TV_FEATURES;
    static {
        Set<String> features = new HashSet<>();
        features.add("android.hardware.camera");
        features.add("android.hardware.camera.autofocus");
        features.add("android.hardware.camera.flash");
        features.add("android.hardware.faketouch");
        features.add("android.hardware.location.gps");
        features.add("android.hardware.nfc");
        features.add("android.hardware.sensor");
        features.add("android.hardware.telephony");
        features.add("android.hardware.touchscreen");
        features.add("android.hardware.type.automotive");
        features.add("android.hardware.type.watch");
        UNSUPPORTED_TV_FEATURES = Collections.unmodifiableSet(features);
    }

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                            + "Any `<uses-feature>` not explicitly marked with `required=\"false\"` is "
                            + "considered required and prevents the app from being installed on devices "
                            + "that do not provide the feature. Android TV devices do not support hardware "
                            + "features such as camera, GPS, NFC, sensors, telephony, and touchscreen. "
                            + "Ensure that any such features are marked as not required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsManifest;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = "AndroidManifest.xml".equals(context.file.getName());
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mIsManifest = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest || !TAG_USES_FEATURE.equals(element.getTagName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty() || !isUnsupportedTvHardware(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, "required");
        if (!"false".equalsIgnoreCase(required)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Unsupported TV hardware feature \"" + name + "\" is required. "
                            + "Consider setting android:required=\"false\".");
        }
    }

    private static boolean isUnsupportedTvHardware(@NonNull String name) {
        if (UNSUPPORTED_TV_FEATURES.contains(name)) {
            return true;
        }
        if (name.startsWith("android.hardware.camera.")) {
            return true;
        }
        if (name.startsWith("android.hardware.sensor.")) {
            return true;
        }
        if (name.startsWith("android.hardware.touchscreen.")) {
            return true;
        }
        if (name.startsWith("android.hardware.faketouch.")) {
            return true;
        }
        return false;
    }
}