package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue UNSUPPORTED_TV_HARDWARE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. " +
            "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the " +
            "device to be installed on. Ensure that any features that might prevent it from being " +
            "installed on a TV device are reviewed and marked as not required in the manifest.",
            Category.COMPLIANCE,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final Set<String> UNSUPPORTED_FEATURES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "android.hardware.touchscreen",
                    "android.hardware.camera",
                    "android.hardware.camera.autofocus",
                    "android.hardware.camera.flash",
                    "android.hardware.camera.front",
                    "android.hardware.camera.any",
                    "android.hardware.telephony",
                    "android.hardware.nfc",
                    "android.hardware.gps",
                    "android.hardware.sensor.light",
                    "android.hardware.sensor.proximity",
                    "android.hardware.sensor.barometer",
                    "android.hardware.sensor.gyroscope",
                    "android.hardware.sensor.stepcounter",
                    "android.hardware.sensor.stepdetector",
                    "android.hardware.sensor.ambient_temperature",
                    "android.hardware.sensor.relative_humidity",
                    "android.hardware.fingerprint",
                    "android.hardware.biometric"
            ))
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isTvApp = false;
        NodeList usesFeatures = root.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                isTvApp = true;
                break;
            }
        }

        if (!isTvApp) {
            NodeList categories = root.getElementsByTagName(SdkConstants.TAG_CATEGORY);
            for (int i = 0; i < categories.getLength(); i++) {
                Element category = (Element) categories.item(i);
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    isTvApp = true;
                    break;
                }
            }
        }

        if (!isTvApp) {
            return;
        }

        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (UNSUPPORTED_FEATURES.contains(name)) {
                String required = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if (!SdkConstants.VALUE_FALSE.equals(required)) {
                    context.report(
                            UNSUPPORTED_TV_HARDWARE,
                            feature,
                            context.getNameLocation(feature),
                            String.format("Expect `android:required=\"false\"` for `%1$s` when targeting Android TV", name)
                    );
                }
            }
        }
    }
}