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
            "Apps require the `android.hardware.touchscreen` feature by default. If " +
            "you want your app to be available on TV, you must also " +
            "explicitly declare that a touchscreen is not required as " +
            "follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
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
        boolean hasLeanbackLauncher = false;
        boolean declaresTouchscreenNotRequired = false;
        Element reportTarget = root;

        NodeList usesFeatures = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                hasLeanbackFeature = true;
                reportTarget = element;
            } else if ("android.hardware.touchscreen".equals(name)) {
                String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if (SdkConstants.VALUE_FALSE.equals(required)) {
                    declaresTouchscreenNotRequired = true;
                }
            }
        }

        NodeList categories = document.getElementsByTagName(SdkConstants.TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
                if (reportTarget == root) {
                    reportTarget = element;
                }
            }
        }

        if ((hasLeanbackFeature || hasLeanbackLauncher) && !declaresTouchscreenNotRequired) {
            context.report(
                    ISSUE,
                    reportTarget,
                    context.getLocation(reportTarget),
                    "Hardware feature `android.hardware.touchscreen` should be declared " +
                    "as not required to support Android TV."
            );
        }
    }
}