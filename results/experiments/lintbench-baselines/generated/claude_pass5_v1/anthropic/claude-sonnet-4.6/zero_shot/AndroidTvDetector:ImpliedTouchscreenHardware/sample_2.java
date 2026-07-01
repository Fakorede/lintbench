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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Detector for the ImpliedTouchscreenHardware issue.
 *
 * <p>Apps require the {@code android.hardware.touchscreen} feature by default. If you want your
 * app to be available on TV, you must also explicitly declare that a touchscreen is not required.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_TV_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String USES_FEATURE_TAG = "uses-feature";
    private static final String USES_FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APPLICATION = "application";

    /** The main issue detected by this detector */
    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want "
                    + "your app to be available on TV, you must also explicitly declare that a "
                    + "touchscreen is not required as follows:\n"
                    + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                    + "android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/guide/topics/manifest/uses-feature-element.html");

    /** Constructs a new {@link AndroidTvDetector} */
    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element manifestElement) {
        // First, check if this app targets Android TV by looking for
        // LEANBACK_LAUNCHER category in any activity's intent filter
        if (!isAndroidTvApp(manifestElement)) {
            return;
        }

        // Check if there's an explicit uses-feature for touchscreen with required="false"
        if (!hasTouchscreenNotRequired(manifestElement)) {
            // Report the issue on the manifest element
            context.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    manifestElement,
                    context.getLocation(manifestElement),
                    "You must explicitly declare the use of the `android.hardware.touchscreen` "
                            + "feature if your app targets TV, as follows: "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`");
        }
    }

    /**
     * Returns true if the manifest targets Android TV (i.e., has an activity with an intent filter
     * containing the LEANBACK_LAUNCHER category).
     */
    private static boolean isAndroidTvApp(Element manifestElement) {
        // Look through application > activity > intent-filter > category
        NodeList applicationNodes = manifestElement.getElementsByTagName(TAG_APPLICATION);
        for (int i = 0; i < applicationNodes.getLength(); i++) {
            Element application = (Element) applicationNodes.item(i);
            NodeList activityNodes = application.getElementsByTagName(TAG_ACTIVITY);
            for (int j = 0; j < activityNodes.getLength(); j++) {
                Element activity = (Element) activityNodes.item(j);
                NodeList intentFilterNodes = activity.getElementsByTagName(TAG_INTENT_FILTER);
                for (int k = 0; k < intentFilterNodes.getLength(); k++) {
                    Element intentFilter = (Element) intentFilterNodes.item(k);
                    NodeList categoryNodes = intentFilter.getElementsByTagName(TAG_CATEGORY);
                    for (int l = 0; l < categoryNodes.getLength(); l++) {
                        Element category = (Element) categoryNodes.item(l);
                        Attr nameAttr = category.getAttributeNodeNS(
                                SdkConstants.ANDROID_URI, ATTR_NAME);
                        if (nameAttr != null
                                && ANDROID_TV_CATEGORY.equals(nameAttr.getValue())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the manifest has a {@code <uses-feature>} element for
     * {@code android.hardware.touchscreen} with {@code android:required="false"}.
     */
    private static boolean hasTouchscreenNotRequired(Element manifestElement) {
        NodeList usesFeatureNodes = manifestElement.getElementsByTagName(USES_FEATURE_TAG);
        for (int i = 0; i < usesFeatureNodes.getLength(); i++) {
            Element usesFeature = (Element) usesFeatureNodes.item(i);
            Attr nameAttr = usesFeature.getAttributeNodeNS(SdkConstants.ANDROID_URI, ATTR_NAME);
            if (nameAttr != null && USES_FEATURE_TOUCHSCREEN.equals(nameAttr.getValue())) {
                Attr requiredAttr = usesFeature.getAttributeNodeNS(
                        SdkConstants.ANDROID_URI, ATTR_REQUIRED);
                if (requiredAttr != null
                        && SdkConstants.VALUE_FALSE.equals(requiredAttr.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }
}