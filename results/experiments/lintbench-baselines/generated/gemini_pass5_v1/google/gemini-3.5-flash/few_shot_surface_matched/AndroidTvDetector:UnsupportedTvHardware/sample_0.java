package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.NODE_CATEGORY;
import static com.android.SdkConstants.NODE_USES_FEATURE;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

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
            "android.hardware.gps",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.light",
            "android.hardware.sensor.proximity",
            "android.hardware.sensor.stepcounter",
            "android.hardware.sensor.stepdetector"
    ));

    private boolean mIsTvApp;
    private final List<Element> mUnsupportedFeaturesRequired = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mIsTvApp = false;
        mUnsupportedFeaturesRequired.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (NODE_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (!VALUE_FALSE.equals(required)) {
                    mIsTvApp = true;
                }
            } else if (UNSUPPORTED_FEATURES.contains(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (!VALUE_FALSE.equals(required)) {
                    mUnsupportedFeaturesRequired.add(element);
                }
            }
        } else if (NODE_CATEGORY.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mIsTvApp) {
            for (Element element : mUnsupportedFeaturesRequired) {
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The hardware feature `" + name + "` is not supported on Android TV "
                                + "and should be declared as optional (required=\"false\")"
                );
            }
        }
    }
}