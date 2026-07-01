package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
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
                    "The `<uses-feature>` element should not require this unsupported TV "
                            + "hardware feature. Any uses-feature not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to be installed "
                            + "on. Ensure that any features that might prevent it from being "
                            + "installed on a TV device are reviewed and marked as not "
                            + "required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.touchscreen.multitouch.jazzhand",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.camera.any",
            "android.hardware.telephony",
            "android.hardware.telephony.cdma",
            "android.hardware.telephony.gsm",
            "android.hardware.nfc",
            "android.hardware.nfc.hce",
            "android.hardware.gps",
            "android.hardware.microphone",
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.compass",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.light",
            "android.hardware.sensor.proximity"
    ));

    private boolean mIsTvApp;
    private final List<Element> mSuspectFeatures = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mSuspectFeatures.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsTvApp && !mSuspectFeatures.isEmpty()) {
            for (Element element : mSuspectFeatures) {
                String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                String message = String.format(
                        "The `%s` feature is not supported on television devices and should be marked as required=\"false\"",
                        name);
                if (context instanceof XmlContext) {
                    XmlContext xmlContext = (XmlContext) context;
                    xmlContext.report(ISSUE, element, xmlContext.getLocation(element), message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name != null && !name.isEmpty()) {
                if ("android.software.leanback".equals(name)) {
                    mIsTvApp = true;
                }
                if (UNSUPPORTED_FEATURES.contains(name)) {
                    String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                    if (!"false".equals(required)) {
                        mSuspectFeatures.add(element);
                    }
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        }
    }
}