package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue UNSUPPORTED_TV_HARDWARE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV "
                    + "hardware feature. Any uses-feature not explicitly marked with "
                    + "`required=\"false\"` is necessary on the device to be installed "
                    + "on. Ensure that any features that might prevent it from being "
                    + "installed on a TV device are reviewed and marked as not "
                    + "required in the manifest.",
            Category.COMPATIBILITY,
            6,
            Severity.WARNING,
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
            "android.hardware.camera.any",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.light",
            "android.hardware.sensor.proximity",
            "android.hardware.sensor.stepcounter",
            "android.hardware.sensor.stepdetector",
            "android.hardware.telephony",
            "android.hardware.telephony.gsm",
            "android.hardware.telephony.cdma",
            "android.hardware.microphone"
    ));

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (!isTvApp(document)) {
            return;
        }

        NodeList features = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (UNSUPPORTED_FEATURES.contains(name)) {
                String required = feature.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                if (!"false".equalsIgnoreCase(required)) {
                    context.report(
                            UNSUPPORTED_TV_HARDWARE,
                            feature,
                            context.getLocation(feature),
                            String.format("Expect `android:required=\"false\"` for %s", name)
                    );
                }
            }
        }
    }

    private boolean isTvApp(Document document) {
        NodeList categories = document.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element category = (Element) categories.item(i);
            String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                return true;
            }
        }
        NodeList features = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                return true;
            }
        }
        return false;
    }
}