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
            "you want your app to be available on TV, you must also explicitly " +
            "declare that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.COMPLIANCE,
            6,
            Severity.ERROR,
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

        boolean hasLeanbackFeature = false;
        boolean hasLeanbackLauncher = false;
        boolean hasTouchscreenDeclaration = false;
        boolean touchscreenRequired = true;
        Node touchscreenNode = null;

        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                hasLeanbackFeature = true;
            } else if ("android.hardware.touchscreen".equals(name)) {
                hasTouchscreenDeclaration = true;
                touchscreenNode = element;
                String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                if ("false".equals(required)) {
                    touchscreenRequired = false;
                }
            }
        }

        NodeList categories = root.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
                break;
            }
        }

        boolean isTvApp = hasLeanbackFeature || hasLeanbackLauncher;
        boolean needsTouchscreenFalse = isTvApp && (!hasTouchscreenDeclaration || touchscreenRequired);

        if (needsTouchscreenFalse) {
            Location location;
            String message = "Hardware feature `android.hardware.touchscreen` is not marked as optional";
            if (touchscreenNode != null) {
                location = context.getLocation(touchscreenNode);
            } else {
                Node reportNode = null;
                for (int i = 0; i < usesFeatures.getLength(); i++) {
                    Element element = (Element) usesFeatures.item(i);
                    if ("android.software.leanback".equals(element.getAttributeNS(SdkConstants.ANDROID_URI, "name"))) {
                        reportNode = element;
                        break;
                    }
                }
                if (reportNode == null) {
                    for (int i = 0; i < categories.getLength(); i++) {
                        Element element = (Element) categories.item(i);
                        if ("android.intent.category.LEANBACK_LAUNCHER".equals(element.getAttributeNS(SdkConstants.ANDROID_URI, "name"))) {
                            reportNode = element;
                            break;
                        }
                    }
                }
                if (reportNode == null) {
                    reportNode = root;
                }
                location = context.getLocation(reportNode);
            }

            context.report(ISSUE, location, message);
        }
    }
}