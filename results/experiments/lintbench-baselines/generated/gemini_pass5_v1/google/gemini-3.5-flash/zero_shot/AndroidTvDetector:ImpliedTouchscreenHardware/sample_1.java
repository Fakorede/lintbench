package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If " +
            "you want your app to be available on TV, you must also explicitly declare " +
            "that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean hasLeanbackFeature = false;
        boolean hasTouchscreenNotRequired = false;
        boolean hasLeanbackLauncher = false;

        Element leanbackFeatureElement = null;
        Element touchscreenFeatureElement = null;
        Element leanbackLauncherElement = null;

        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Node node = usesFeatures.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                    hasLeanbackFeature = true;
                    leanbackFeatureElement = element;
                } else if ("android.hardware.touchscreen".equals(name)) {
                    touchscreenFeatureElement = element;
                    String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                    if ("false".equals(required)) {
                        hasTouchscreenNotRequired = true;
                    }
                }
            }
        }

        NodeList categories = root.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Node node = categories.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    hasLeanbackLauncher = true;
                    leanbackLauncherElement = element;
                }
            }
        }

        if ((hasLeanbackFeature || hasLeanbackLauncher) && !hasTouchscreenNotRequired) {
            Location location;
            if (touchscreenFeatureElement != null) {
                location = context.getLocation(touchscreenFeatureElement);
            } else if (leanbackFeatureElement != null) {
                location = context.getLocation(leanbackFeatureElement);
            } else if (leanbackLauncherElement != null) {
                location = context.getLocation(leanbackLauncherElement);
            } else {
                location = context.getLocation(root);
            }

            context.report(
                    ISSUE,
                    location,
                    "An app that should be available on TV must explicitly declare that " +
                    "a touchscreen is not required by adding `<uses-feature " +
                    "android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>` " +
                    "to the manifest."
            );
        }
    }
}