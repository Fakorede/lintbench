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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "By default, Google Play assumes that your app requires a touchscreen. If your " +
            "app is intended for Android TV, you must explicitly declare that a touchscreen " +
            "is not required by adding `<uses-feature android:name=\"android.hardware.touchscreen\" " +
            "android:required=\"false\" />` to the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isTvApp = false;
        Element tvTriggerElement = null;

        NodeList features = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                isTvApp = true;
                tvTriggerElement = feature;
                break;
            }
        }

        if (!isTvApp) {
            NodeList categories = root.getElementsByTagName("category");
            for (int i = 0; i < categories.getLength(); i++) {
                Element category = (Element) categories.item(i);
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    isTvApp = true;
                    tvTriggerElement = category;
                    break;
                }
            }
        }

        if (!isTvApp) {
            return;
        }

        Element touchscreenFeature = null;
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.hardware.touchscreen".equals(name)) {
                touchscreenFeature = feature;
                break;
            }
        }

        if (touchscreenFeature == null) {
            context.report(
                    ISSUE,
                    tvTriggerElement != null ? tvTriggerElement : root,
                    context.getLocation(tvTriggerElement != null ? tvTriggerElement : root),
                    "An app that supports Android TV must explicitly declare that it does not require a touchscreen by adding `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\" />` to the manifest."
            );
        } else {
            String required = touchscreenFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            if (!"false".equals(required)) {
                context.report(
                        ISSUE,
                        touchscreenFeature,
                        context.getLocation(touchscreenFeature),
                        "An app that supports Android TV must set `android:required=\"false\"` for the `android.hardware.touchscreen` feature."
                );
            }
        }
    }
}