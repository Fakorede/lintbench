package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The <uses-feature> element should not require an unsupported TV hardware "
                            + "feature. Any <uses-feature> not explicitly marked with "
                            + "required=\"false\" is required on the device in order to install "
                            + "the app. Ensure that features which may prevent installation on "
                            + "TV devices are reviewed and marked as not required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> UNSUPPORTED_TV_FEATURES;

    static {
        Set<String> features = new HashSet<>(
                Arrays.asList(
                        "android.hardware.touchscreen",
                        "android.hardware.touchscreen.multitouch",
                        "android.hardware.touchscreen.multitouch.distinct",
                        "android.hardware.touchscreen.multitouch.jazzhand",
                        "android.hardware.faketouch",
                        "android.hardware.faketouch.multitouch.distinct",
                        "android.hardware.faketouch.multitouch.jazzhand",
                        "android.hardware.telephony",
                        "android.hardware.camera",
                        "android.hardware.camera.any",
                        "android.hardware.camera.autofocus",
                        "android.hardware.camera.flash",
                        "android.hardware.camera.front",
                        "android.hardware.nfc",
                        "android.hardware.location.gps",
                        "android.hardware.location.network",
                        "android.hardware.sensor.accelerometer",
                        "android.hardware.sensor.barometer",
                        "android.hardware.sensor.compass",
                        "android.hardware.sensor.gyroscope",
                        "android.hardware.sensor.light",
                        "android.hardware.sensor.proximity",
                        "android.hardware.sensor.stepcounter",
                        "android.hardware.sensor.stepdetector",
                        "android.hardware.microphone"));
        UNSUPPORTED_TV_FEATURES = Collections.unmodifiableSet(features);
    }

    private List<String> mUnsupportedFeatures;
    private Location mFirstLocation;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mUnsupportedFeatures = new ArrayList<>();
        mFirstLocation = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            name = element.getAttribute(ATTR_NAME);
        }
        if (name == null || name.isEmpty() || !UNSUPPORTED_TV_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if (required == null) {
            required = element.getAttribute(ATTR_REQUIRED);
        }
        if (required != null && required.trim().equalsIgnoreCase("false")) {
            return;
        }

        if (mFirstLocation == null) {
            mFirstLocation = context.getLocation(element);
        }
        mUnsupportedFeatures.add(name);
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mUnsupportedFeatures.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("Unsupported TV hardware feature");
        if (mUnsupportedFeatures.size() > 1) {
            message.append("s");
        }
        message.append(" required in <uses-feature>: ");
        for (int i = 0; i < mUnsupportedFeatures.size(); i++) {
            if (i > 0) {
                message.append(", ");
            }
            message.append(mUnsupportedFeatures.get(i));
        }
        message.append(". These features may not be available on TV devices; mark them with ")
                .append("required=\"false\" or remove them.");

        context.report(ISSUE, mFirstLocation, message.toString());
    }
}