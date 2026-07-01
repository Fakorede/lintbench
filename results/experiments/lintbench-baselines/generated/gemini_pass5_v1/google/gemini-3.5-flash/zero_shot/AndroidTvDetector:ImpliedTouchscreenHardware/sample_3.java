package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
            "Apps require the `android.hardware.touchscreen` feature by default. If you want " +
            "your app to be available on TV, you must also explicitly declare that a touchscreen " +
            "is not required as follows: `<uses-feature android:name=\"android.hardware.touchscreen\" " +
            "android:required=\"false\"/>`",
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

        boolean isTvApp = false;
        boolean hasTouchscreenFalse = false;
        Element touchscreenElement = null;
        Element tvTriggerElement = null;

        NodeList usesFeatures = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                isTvApp = true;
                if (tvTriggerElement == null) {
                    tvTriggerElement = element;
                }
            } else if ("android.hardware.touchscreen".equals(name)) {
                touchscreenElement = element;
                String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if ("false".equals(required)) {
                    hasTouchscreenFalse = true;
                }
            }
        }

        NodeList categories = document.getElementsByTagName(SdkConstants.TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                isTvApp = true;
                if (tvTriggerElement == null) {
                    tvTriggerElement = element;
                }
            }
        }

        if (isTvApp && !hasTouchscreenFalse) {
            if (touchscreenElement != null) {
                context.report(
                        ISSUE,
                        touchscreenElement,
                        context.getLocation(touchscreenElement),
                        "Touchscreen is required by default. For TV compatibility, set `android:required=\"false\"`"
                );
            } else {
                Element target = tvTriggerElement != null ? tvTriggerElement : root;
                context.report(
                        ISSUE,
                        target,
                        context.getLocation(target),
                        "An app that supports TV must explicitly declare that touchscreen is not required: " +
                        "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`"
                );
            }
        }
    }
}