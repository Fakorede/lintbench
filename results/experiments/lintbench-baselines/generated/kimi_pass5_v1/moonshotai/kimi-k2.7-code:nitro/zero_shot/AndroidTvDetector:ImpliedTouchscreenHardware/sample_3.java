package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String HARDWARE_TELEVISION = "android.hardware.type.television";
    private static final String SOFTWARE_LEANBACK = "android.software.leanback";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you "
                            + "want your app to be available on TV, you must also explicitly "
                            + "declare that a touchscreen is not required as follows:\n"
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html");

    @Override
    public void visitDocument(@NonNull XmlContext context) {
        Element root = context.document.getDocumentElement();
        if (root == null || !"manifest".equals(root.getTagName())) {
            return;
        }

        if (!isTvManifest(root)) {
            return;
        }

        if (!hasOptionalTouchscreen(root)) {
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "Apps that target Android TV must declare that a touchscreen is not required; "
                            + "add `<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`");
        }
    }

    private static boolean isTvManifest(@NonNull Element root) {
        NodeList features = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (HARDWARE_TELEVISION.equals(name) || SOFTWARE_LEANBACK.equals(name)) {
                return true;
            }
        }

        NodeList activities = root.getElementsByTagName(TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER);
            for (int j = 0; j < intentFilters.getLength(); j++) {
                Element intentFilter = (Element) intentFilters.item(j);
                NodeList categories = intentFilter.getElementsByTagName(TAG_CATEGORY);
                for (int k = 0; k < categories.getLength(); k++) {
                    Element category = (Element) categories.item(k);
                    String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean hasOptionalTouchscreen(@NonNull Element root) {
        NodeList features = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (HARDWARE_TOUCHSCREEN.equals(name)) {
                String required = feature.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (VALUE_FALSE.equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }
}