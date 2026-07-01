package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV " +
            "hardware feature. Any uses-feature not explicitly marked with " +
            "`required=\"false\"` is necessary on the device to be installed " +
            "on. Ensure that any features that might prevent it from being " +
            "installed on a TV device are reviewed and marked as not " +
            "required in the manifest.",
            Category.COMPATIBILITY,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.touchscreen.multitouch.jazzhand",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.front",
            "android.hardware.camera.flash",
            "android.hardware.camera.any",
            "android.hardware.camera.external",
            "android.hardware.telephony",
            "android.hardware.telephony.cdma",
            "android.hardware.telephony.gsm",
            "android.hardware.telephony.mbms",
            "android.hardware.nfc",
            "android.hardware.nfc.hce",
            "android.hardware.nfc.hcef",
            "android.hardware.location.gps",
            "android.hardware.microphone",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.light",
            "android.hardware.sensor.proximity",
            "android.hardware.sensor.stepcounter",
            "android.hardware.sensor.stepdetector",
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.compass",
            "android.hardware.sensor.ambient_temperature",
            "android.hardware.sensor.relative_humidity",
            "android.hardware.sensor.heartrate",
            "android.hardware.sensor.heartrate.ecg"
    ));

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null || !SdkConstants.TAG_MANIFEST.equals(root.getTagName())) {
            return;
        }

        ManifestInfo info = new ManifestInfo();
        collectManifestInfo(root, info);

        if (info.isTvApp) {
            for (Element usesFeature : info.usesFeatures) {
                String name = usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (UNSUPPORTED_FEATURES.contains(name)) {
                    String required = usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                    if (required.isEmpty() || !required.equals("false")) {
                        LintFix fix = fix()
                                .set(SdkConstants.ANDROID_URI, "required", "false")
                                .build();
                        context.report(
                                ISSUE,
                                usesFeature,
                                context.getNameLocation(usesFeature),
                                "The hardware feature `" + name + "` is not supported on Android TV and should not be required. " +
                                "Mark it as `required=\"false\"` to allow installation on TV devices.",
                                fix
                        );
                    }
                }
            }
        }
    }

    private void collectManifestInfo(Element element, ManifestInfo info) {
        String tagName = element.getTagName();
        if (SdkConstants.TAG_USES_FEATURE.equals(tagName)) {
            info.usesFeatures.add(element);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                info.isTvApp = true;
            }
        } else if (SdkConstants.TAG_CATEGORY.equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                info.isTvApp = true;
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectManifestInfo((Element) child, info);
            }
        }
    }

    private static class ManifestInfo {
        boolean isTvApp = false;
        final List<Element> usesFeatures = new ArrayList<>();
    }
}