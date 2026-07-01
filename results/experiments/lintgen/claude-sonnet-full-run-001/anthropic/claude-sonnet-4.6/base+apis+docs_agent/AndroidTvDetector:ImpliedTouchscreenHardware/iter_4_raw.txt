package com.android.tools.lint.checks;

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

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String CATEGORY_LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String TAG_CATEGORY = "category";

    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want " +
            "your app to be available on TV, you must also explicitly declare that a touchscreen " +
            "is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" " +
            "android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/guide/topics/manifest/uses-feature-element.html");

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Check if this manifest has a LEANBACK_LAUNCHER category
        if (!hasLeanbackLauncherCategory(document)) {
            return;
        }

        // Check if touchscreen is explicitly marked as not required
        if (!hasTouchscreenNotRequired(document)) {
            Element manifestElement = document.getDocumentElement();
            context.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    manifestElement,
                    context.getLocation(manifestElement),
                    "Hardware feature `android.hardware.touchscreen` not explicitly marked as " +
                    "optional. Apps require the `android.hardware.touchscreen` feature by " +
                    "default. If you want your app to be available on TV, you must also " +
                    "explicitly declare that a touchscreen is not required as follows: " +
                    "`<uses-feature android:name=\"android.hardware.touchscreen\" " +
                    "android:required=\"false\"/>`"
            );
        }
    }

    private boolean hasLeanbackLauncherCategory(Document document) {
        NodeList categories = document.getElementsByTagName(TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Element category = (Element) categories.item(i);
            String name = getAttributeValue(category, ATTR_NAME);
            if (CATEGORY_LEANBACK.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String getAttributeValue(Element element, String attrName) {
        String value = element.getAttributeNS(ANDROID_NS, attrName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute(attrName);
        }
        return value;
    }

    private boolean hasTouchscreenNotRequired(Document document) {
        NodeList usesFeatures = document.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element usesFeature = (Element) usesFeatures.item(i);
            String name = getAttributeValue(usesFeature, ATTR_NAME);
            if (FEATURE_TOUCHSCREEN.equals(name)) {
                String required = getAttributeValue(usesFeature, ATTR_REQUIRED);
                if ("false".equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }
}