package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If " +
            "you want your app to be available on TV, you must also " +
            "explicitly declare that a touchscreen is not required as " +
            "follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isTvApp = false;
        boolean hasTouchscreenDecl = false;
        boolean touchscreenRequired = true;
        Element touchscreenElement = null;

        NodeList categories = document.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element category = (Element) categories.item(i);
            String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                isTvApp = true;
                break;
            }
        }

        NodeList features = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                isTvApp = true;
            }
            if ("android.hardware.touchscreen".equals(name)) {
                hasTouchscreenDecl = true;
                touchscreenElement = feature;
                String required = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if ("false".equals(required)) {
                    touchscreenRequired = false;
                }
            }
        }

        if (isTvApp) {
            if (!hasTouchscreenDecl || touchscreenRequired) {
                Location location;
                if (touchscreenElement != null) {
                    location = context.getLocation(touchscreenElement);
                } else {
                    location = context.getLocation(root);
                }
                context.report(
                        ISSUE,
                        location,
                        "An app that is available on TV must explicitly declare that a touchscreen is not required: " +
                        "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`"
                );
            }
        }
    }
}