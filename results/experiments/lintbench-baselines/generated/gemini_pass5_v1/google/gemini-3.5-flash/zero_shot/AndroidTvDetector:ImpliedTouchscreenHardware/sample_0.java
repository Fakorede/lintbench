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
import java.util.EnumSet;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you " +
            "want your app to be available on TV, you must also explicitly declare that a " +
            "touchscreen is not required as follows: `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.COMPATIBILITY,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        NodeList categories = root.getElementsByTagName("category");

        Element leanbackFeatureElement = null;
        Element leanbackLauncherElement = null;
        Element touchscreenElement = null;
        boolean touchscreenRequired = true;

        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                leanbackFeatureElement = element;
            } else if ("android.hardware.touchscreen".equals(name)) {
                touchscreenElement = element;
                String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                if ("false".equals(required)) {
                    touchscreenRequired = false;
                }
            }
        }

        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                leanbackLauncherElement = element;
            }
        }

        boolean isTvApp = (leanbackFeatureElement != null) || (leanbackLauncherElement != null);

        if (isTvApp && touchscreenRequired) {
            Location location;
            String message;
            if (touchscreenElement != null) {
                location = context.getLocation(touchscreenElement);
                message = "The touchscreen feature should be declared as not required for TV compatibility: `android:required=\"false\"`";
            } else {
                Element target = leanbackFeatureElement != null ? leanbackFeatureElement : leanbackLauncherElement;
                location = context.getLocation(target);
                message = "An app that supports TV should explicitly declare that touchscreen is not required: `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\" />`";
            }
            context.report(ISSUE, location, message);
        }
    }

    @NotNull
    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.MANIFEST_SCOPE;
    }
}