package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "UnsupportedTvHardware",
        "Unsupported TV Hardware Feature",
        "The `<uses-feature>` element should not require this unsupported TV " +
        "hardware feature. Any uses-feature not explicitly marked with " +
        "`required=\"false\"` is necessary on the device to be installed " +
        "on. Ensure that any features that might prevent it from being " +
        "installed on a TV device are reviewed and marked as not " +
        "required in the manifest.",
        Category.COMPLIANCE,
        6,
        Severity.ERROR,
        new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE
        )
    );

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
        "android.hardware.touchscreen",
        "android.hardware.touchscreen.multitouch",
        "android.hardware.touchscreen.multitouch.distinct",
        "android.hardware.touchscreen.multitouch.jazzhand",
        "android.hardware.camera",
        "android.hardware.camera.autofocus",
        "android.hardware.camera.flash",
        "android.hardware.camera.front",
        "android.hardware.camera.external",
        "android.hardware.telephony",
        "android.hardware.telephony.gsm",
        "android.hardware.telephony.cdma",
        "android.hardware.nfc",
        "android.hardware.nfc.hce",
        "android.hardware.location.gps",
        "android.hardware.location.navigation",
        "android.hardware.sensor.barometer",
        "android.hardware.sensor.compass",
        "android.hardware.sensor.gyroscope",
        "android.hardware.sensor.light",
        "android.hardware.sensor.proximity",
        "android.hardware.sensor.stepcounter",
        "android.hardware.sensor.stepdetector"
    ));

    private boolean mIsTvApp = false;
    private final List<PendingViolation> mPendingViolations = new ArrayList<>();

    private static class PendingViolation {
        final Element element;
        final Location location;
        final String featureName;

        PendingViolation(Element element, Location location, String featureName) {
            this.element = element;
            this.location = location;
            this.featureName = featureName;
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mPendingViolations.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("category".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                mIsTvApp = true;
            }
            if (UNSUPPORTED_FEATURES.contains(name)) {
                String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                if (!"false".equals(required)) {
                    Attr nameAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "name");
                    Location location = nameAttr != null ? context.getLocation(nameAttr) : context.getNameLocation(element);
                    mPendingViolations.add(new PendingViolation(element, location, name));
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsTvApp && !mPendingViolations.isEmpty() && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            for (PendingViolation violation : mPendingViolations) {
                String message = String.format(
                    "Expects `%s` hardware feature, which is not supported on TV. " +
                    "Consider adding `android:required=\"false\"` to prevent filtering on TV devices.",
                    violation.featureName
                );

                LintFix fix = fix()
                    .set(SdkConstants.ANDROID_URI, "required", "false")
                    .build();

                xmlContext.report(ISSUE, violation.element, violation.location, message, fix);
            }
        }
        mPendingViolations.clear();
        mIsTvApp = false;
    }
}