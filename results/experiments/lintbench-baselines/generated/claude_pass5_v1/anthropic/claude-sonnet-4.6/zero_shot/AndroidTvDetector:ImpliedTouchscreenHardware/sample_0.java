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
 * Detector for Android TV compatibility issues related to touchscreen hardware requirements.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTIVITY = "activity";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_REQUIRED = "required";
    private static final String ANDROID_NS = SdkConstants.ANDROID_URI;

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
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/guide/topics/manifest/uses-feature-element.html");

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element manifestElement) {
        // Check if this app targets Android TV (has LEANBACK_LAUNCHER category)
        if (!hasLeanbackLauncherCategory(manifestElement)) {
            return;
        }

        // Check if touchscreen is explicitly declared as not required
        if (!isTouchscreenOptional(manifestElement)) {
            // Find a good location to report the error
            Element reportElement = manifestElement;
            // Try to find a uses-feature element to attach the error to, or use manifest
            NodeList usesFeatureNodes = manifestElement.getElementsByTagName(TAG_USES_FEATURE);
            context.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    reportElement,
                    context.getLocation(reportElement),
                    "You must explicitly declare that `android.hardware.touchscreen` is not " +
                    "required for your app to be available on TV. " +
                    "Add `<uses-feature android:name=\"android.hardware.touchscreen\" " +
                    "android:required=\"false\"/>` to your manifest.");
        }
    }

    /**
     * Returns true if the manifest contains an activity with an intent-filter
     * that includes the LEANBACK_LAUNCHER category, indicating this app targets Android TV.
     */
    private boolean hasLeanbackLauncherCategory(Element manifestElement) {
        // Look through all application elements
        NodeList applicationNodes = manifestElement.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        for (int i = 0; i < applicationNodes.getLength(); i++) {
            Element applicationElement = (Element) applicationNodes.item(i);
            // Look through all activity elements
            NodeList activityNodes = applicationElement.getElementsByTagName(TAG_ACTIVITY);
            for (int j = 0; j < activityNodes.getLength(); j++) {
                Element activityElement = (Element) activityNodes.item(j);
                if (hasLeanbackCategory(activityElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given activity element has an intent-filter with
     * the LEANBACK_LAUNCHER category.
     */
    private boolean hasLeanbackCategory(Element activityElement) {
        NodeList intentFilterNodes = activityElement.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilterNodes.getLength(); i++) {
            Element intentFilterElement = (Element) intentFilterNodes.item(i);
            NodeList categoryNodes = intentFilterElement.getElementsByTagName(TAG_CATEGORY);
            for (int j = 0; j < categoryNodes.getLength(); j++) {
                Element categoryElement = (Element) categoryNodes.item(j);
                String nameValue = categoryElement.getAttributeNS(ANDROID_NS, ATTRIBUTE_NAME);
                if (CATEGORY_LEANBACK_LAUNCHER.equals(nameValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the manifest explicitly declares that the touchscreen hardware
     * feature is not required (android:required="false").
     */
    private boolean isTouchscreenOptional(Element manifestElement) {
        NodeList usesFeatureNodes = manifestElement.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatureNodes.getLength(); i++) {
            Element usesFeatureElement = (Element) usesFeatureNodes.item(i);
            String nameValue = usesFeatureElement.getAttributeNS(ANDROID_NS, ATTRIBUTE_NAME);
            if (HARDWARE_TOUCHSCREEN.equals(nameValue)) {
                String requiredValue = usesFeatureElement.getAttributeNS(ANDROID_NS, ATTRIBUTE_REQUIRED);
                // If required attribute is explicitly set to false, touchscreen is optional
                if ("false".equals(requiredValue)) {
                    return true;
                }
            }
        }
        return false;
    }
}