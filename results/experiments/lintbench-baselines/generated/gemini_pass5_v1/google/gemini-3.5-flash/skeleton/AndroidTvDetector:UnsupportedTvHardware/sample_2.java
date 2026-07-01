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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV  "
                            + "hardware feature. Any uses-feature not explicitly marked with  "
                            + "`required=\"false\"` is necessary on the device to be installed  "
                            + "on. Ensure that any features that might prevent it from being  "
                            + "installed on a TV device are reviewed and marked as not  "
                            + "required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.front",
            "android.hardware.camera.flash",
            "android.hardware.camera.level.full",
            "android.hardware.camera.capability.manual_post_processing",
            "android.hardware.camera.capability.manual_sensor",
            "android.hardware.camera.capability.raw",
            "android.hardware.camera.external",
            "android.hardware.telephony",
            "android.hardware.telephony.cdma",
            "android.hardware.telephony.gsm",
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.touchscreen.multitouch.jazzhand",
            "android.hardware.nfc",
            "android.hardware.nfc.hce",
            "android.hardware.location.gps",
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.compass",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.proximity",
            "android.hardware.sensor.stepcounter",
            "android.hardware.sensor.stepdetector",
            "android.hardware.sensor.heartrate",
            "android.hardware.sensor.heartrate.ecg",
            "android.hardware.sensor.ambient_temperature",
            "android.hardware.sensor.relative_humidity",
            "android.hardware.sensor.light"
    ));

    private boolean mIsTvApp = false;
    private final List<Element> mUnsupportedFeatures = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mUnsupportedFeatures.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsTvApp && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            for (Element element : mUnsupportedFeatures) {
                String featureName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (featureName.isEmpty()) {
                    featureName = element.getAttribute("android:name");
                }
                xmlContext.report(
                        ISSUE,
                        element,
                        xmlContext.getLocation(element),
                        "The hardware feature `" + featureName + "` is not supported on Google TV / Android TV. You should set `android:required=\"false\"`."
                );
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if ("android.software.leanback".equals(name)) {
                mIsTvApp = true;
            } else if (UNSUPPORTED_FEATURES.contains(name)) {
                String requiredStr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                if (requiredStr.isEmpty()) {
                    requiredStr = element.getAttribute("android:required");
                }
                boolean required = !"false".equalsIgnoreCase(requiredStr);
                if (required) {
                    mUnsupportedFeatures.add(element);
                }
            }
        }
    }
}