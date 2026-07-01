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
            "Apps require the `android.hardware.touchscreen` feature by default. " +
            "If you want your app to be available on TV, you must also explicitly " +
            "declare that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.COMPATIBILITY,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isTvApp = false;
        boolean hasTouchscreenHardwareDeclared = false;
        boolean touchscreenRequired = true;
        Element touchscreenElement = null;

        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                isTvApp = true;
            } else if ("android.hardware.touchscreen".equals(name)) {
                hasTouchscreenHardwareDeclared = true;
                touchscreenElement = element;
                String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                if ("false".equals(required)) {
                    touchscreenRequired = false;
                }
            }
        }

        if (!isTvApp) {
            NodeList categories = root.getElementsByTagName("category");
            for (int i = 0; i < categories.getLength(); i++) {
                Element element = (Element) categories.item(i);
                String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    isTvApp = true;
                    break;
                }
            }
        }

        if (isTvApp) {
            if (!hasTouchscreenHardwareDeclared || touchscreenRequired) {
                Location location;
                if (touchscreenElement != null) {
                    location = context.getLocation(touchscreenElement);
                } else {
                    location = context.getLocation(root);
                }
                context.report(
                        ISSUE,
                        location,
                        "Hardware feature `android.hardware.touchscreen` must be declared as not required for TV compatibility"
                );
            }
        }
    }
}