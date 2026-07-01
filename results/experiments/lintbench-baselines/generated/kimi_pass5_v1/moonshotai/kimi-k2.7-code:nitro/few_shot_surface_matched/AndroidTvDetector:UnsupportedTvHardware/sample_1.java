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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String USES_FEATURE = "uses-feature";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_FALSE = "false";

    private static final List<String> UNSUPPORTED_FEATURES;
    static {
        List<String> features = new ArrayList<>();
        features.add("android.hardware.telephony");
        features.add("android.hardware.camera");
        features.add("android.hardware.camera.autofocus");
        features.add("android.hardware.camera.flash");
        features.add("android.hardware.nfc");
        features.add("android.hardware.location.gps");
        features.add("android.hardware.microphone");
        features.add("android.hardware.sensor.accelerometer");
        features.add("android.hardware.sensor.barometer");
        features.add("android.hardware.sensor.compass");
        features.add("android.hardware.sensor.gyroscope");
        features.add("android.hardware.sensor.light");
        features.add("android.hardware.sensor.proximity");
        features.add("android.hardware.touchscreen");
        features.add("android.hardware.faketouch");
        features.add("android.hardware.screen.portrait");
        UNSUPPORTED_FEATURES = Collections.unmodifiableList(features);
    }

    private final List<Element> mOffendingFeatures = new ArrayList<>();

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require an unsupported TV hardware"
                            + " feature. Any `<uses-feature>` not explicitly marked with"
                            + " `required=\"false\"` is treated as required and can prevent the"
                            + " app from being installed on TV devices. Ensure that any TV"
                            + " hardware features that are not supported are marked as not"
                            + " required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mOffendingFeatures.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = getFeatureName(element);
        if (name.isEmpty()) {
            return;
        }

        if (!isUnsupportedTvFeature(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if (required.isEmpty()) {
            required = element.getAttribute(ATTR_REQUIRED);
        }
        if (VALUE_FALSE.equalsIgnoreCase(required)) {
            return;
        }

        mOffendingFeatures.add(element);
    }

    @Override
    public void afterCheckFile(Context context) {
        XmlContext xmlContext = (XmlContext) context;
        for (Element element : mOffendingFeatures) {
            String name = getFeatureName(element);
            Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            xmlContext.report(
                    ISSUE,
                    nameAttr != null ? nameAttr : element,
                    xmlContext.getLocation(nameAttr != null ? nameAttr : element),
                    String.format(
                            "The hardware feature `%1$s` is not supported on TV devices. Mark the"
                                    + " `<uses-feature>` element with"
                                    + " `android:required=\"false\"` to allow the app to be"
                                    + " installed on TV devices.",
                            name));
        }
        mOffendingFeatures.clear();
    }

    private static String getFeatureName(Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name.isEmpty()) {
            name = element.getAttribute(ATTR_NAME);
        }
        return name != null ? name : "";
    }

    private static boolean isUnsupportedTvFeature(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("android.hardware.telephony.")) {
            return true;
        }
        if (lower.startsWith("android.hardware.camera.")) {
            return true;
        }
        if (lower.startsWith("android.hardware.touchscreen.")) {
            return true;
        }
        for (String unsupported : UNSUPPORTED_FEATURES) {
            if (unsupported.equals(lower)) {
                return true;
            }
        }
        return false;
    }
}