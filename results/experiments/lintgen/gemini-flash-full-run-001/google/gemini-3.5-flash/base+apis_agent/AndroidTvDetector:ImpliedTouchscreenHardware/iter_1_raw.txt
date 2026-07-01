package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If " +
            "you want your app to be available on TV, you must also " +
            "explicitly declare that a touchscreen is not required as " +
            "follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" " +
            "android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    public static final Issue ISSUE = IMPLIED_TOUCHSCREEN_HARDWARE;

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean hasLeanbackLauncher = false;
        boolean hasLeanbackFeature = false;
        boolean hasTvFeature = false;
        boolean hasTouchscreenFalse = false;
        Node leanbackLauncherNode = null;
        Node tvFeatureNode = null;
        Node leanbackFeatureNode = null;

        NodeList categoryNodes = document.getElementsByTagName("category");
        for (int i = 0; i < categoryNodes.getLength(); i++) {
            Element category = (Element) categoryNodes.item(i);
            String name = category.getAttributeNS(ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
                leanbackLauncherNode = category;
                break;
            }
        }

        NodeList featureNodes = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < featureNodes.getLength(); i++) {
            Element feature = (Element) featureNodes.item(i);
            String name = feature.getAttributeNS(ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                hasLeanbackFeature = true;
                leanbackFeatureNode = feature;
            } else if ("android.hardware.type.television".equals(name)) {
                hasTvFeature = true;
                tvFeatureNode = feature;
            } else if ("android.hardware.touchscreen".equals(name)) {
                String required = feature.getAttributeNS(ANDROID_URI, "required");
                if ("false".equals(required)) {
                    hasTouchscreenFalse = true;
                }
            }
        }

        if ((hasLeanbackLauncher || hasTvFeature || hasLeanbackFeature) && !hasTouchscreenFalse) {
            Location location;
            if (leanbackLauncherNode != null) {
                location = context.getLocation(leanbackLauncherNode);
            } else if (tvFeatureNode != null) {
                location = context.getLocation(tvFeatureNode);
            } else if (leanbackFeatureNode != null) {
                location = context.getLocation(leanbackFeatureNode);
            } else {
                location = context.getLocation(root);
            }
            context.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    location,
                    "An app that is available on TV must explicitly declare that a touchscreen is not required by adding `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\" />` to the manifest."
            );
        }
    }
}