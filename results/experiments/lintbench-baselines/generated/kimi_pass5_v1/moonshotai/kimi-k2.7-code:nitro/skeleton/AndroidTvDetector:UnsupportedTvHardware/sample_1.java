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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Set<String> UNSUPPORTED_TV_FEATURES;
    static {
        Set<String> features = new HashSet<>();
        features.add("android.hardware.camera");
        features.add("android.hardware.camera.autofocus");
        features.add("android.hardware.camera.flash");
        features.add("android.hardware.camera.front");
        features.add("android.hardware.location");
        features.add("android.hardware.location.gps");
        features.add("android.hardware.microphone");
        features.add("android.hardware.nfc");
        features.add("android.hardware.sensor.accelerometer");
        features.add("android.hardware.sensor.barometer");
        features.add("android.hardware.sensor.compass");
        features.add("android.hardware.sensor.gyroscope");
        features.add("android.hardware.sensor.light");
        features.add("android.hardware.sensor.proximity");
        features.add("android.hardware.sensor.step_counter");
        features.add("android.hardware.sensor.step_detector");
        features.add("android.hardware.telephony");
        features.add("android.hardware.touchscreen");
        features.add("android.hardware.touchscreen.multitouch");
        features.add("android.hardware.touchscreen.multitouch.distinct");
        features.add("android.hardware.touchscreen.multitouch.jazzhand");
        features.add("android.hardware.type.automotive");
        features.add("android.hardware.type.watch");
        features.add("android.hardware.screen.portrait");
        features.add("android.software.car_mode");
        UNSUPPORTED_TV_FEATURES = Collections.unmodifiableSet(features);
    }

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require an unsupported TV hardware "
                            + "feature. Any `<uses-feature>` not explicitly marked with "
                            + "`required=\"false\"` is treated as required for installation. "
                            + "Ensure that features which are not supported on TV devices are "
                            + "reviewed and marked as not required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file setup required.
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No per-file cleanup required.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"AndroidManifest.xml".equals(context.file.getName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if (!UNSUPPORTED_TV_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, "required");
        if ("false".equals(required)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The feature \""
                        + name
                        + "\" is not supported on Android TV and should be marked with "
                        + "`required=\"false\"` or removed from the manifest.");
    }
}