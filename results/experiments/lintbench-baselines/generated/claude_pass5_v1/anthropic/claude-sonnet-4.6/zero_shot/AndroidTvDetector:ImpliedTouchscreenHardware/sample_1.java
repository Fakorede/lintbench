package com.android.tools.lint.checks;

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
 *
 * <p>Apps require the {@code android.hardware.touchscreen} feature by default. If you want your app
 * to be available on TV, you must explicitly declare that a touchscreen is not required.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_TV_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String USES_FEATURE_TAG = "uses-feature";
    private static final String USES_INTENT_TAG = "intent-filter";
    private static final String CATEGORY_TAG = "category";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_REQUIRED = "required";
    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";

    /** Issue: Touchscreen not optional for TV apps */
    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If "
                            + "you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as "
                            + "follows:\n"
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html");

    /** Constructs a new {@link AndroidTvDetector}. */
    public AndroidTvDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element manifestElement) {
        // Check if this manifest targets Android TV by looking for LEANBACK_LAUNCHER category
        if (!targetsTv(manifestElement)) {
            return;
        }

        // Check if touchscreen is explicitly declared as not required
        if (!isTouchscreenOptional(manifestElement)) {
            // Report the issue on the manifest element
            context.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    manifestElement,
                    context.getLocation(manifestElement),
                    "You must explicitly declare that a touchscreen is not required for "
                            + "TV compatibility. Add: "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`");
        }
    }

    /**
     * Returns true if the manifest targets Android TV by checking for LEANBACK_LAUNCHER category
     * in any activity's intent filters.
     */
    private boolean targetsTv(Element manifestElement) {
        // Look through the application element for activities with LEANBACK_LAUNCHER category
        NodeList applicationNodes = manifestElement.getElementsByTagName(TAG_APPLICATION);
        for (int i = 0; i < applicationNodes.getLength(); i++) {
            Element application = (Element) applicationNodes.item(i);
            NodeList activityNodes = application.getElementsByTagName(TAG_ACTIVITY);
            for (int j = 0; j < activityNodes.getLength(); j++) {
                Element activity = (Element) activityNodes.item(j);
                if (activityTargetsTv(activity)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given activity element contains an intent-filter with the
     * LEANBACK_LAUNCHER category.
     */
    private boolean activityTargetsTv(Element activityElement) {
        NodeList intentFilters = activityElement.getElementsByTagName(USES_INTENT_TAG);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList categories = intentFilter.getElementsByTagName(CATEGORY_TAG);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(ANDROID_NS, ATTRIBUTE_NAME);
                if (ANDROID_TV_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the manifest explicitly declares that the touchscreen hardware feature is not
     * required (android:required="false").
     */
    private boolean isTouchscreenOptional(Element manifestElement) {
        NodeList usesFeatures = manifestElement.getElementsByTagName(USES_FEATURE_TAG);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element usesFeature = (Element) usesFeatures.item(i);
            String name = usesFeature.getAttributeNS(ANDROID_NS, ATTRIBUTE_NAME);
            if (HARDWARE_TOUCHSCREEN.equals(name)) {
                String required = usesFeature.getAttributeNS(ANDROID_NS, ATTRIBUTE_REQUIRED);
                // If required is explicitly set to "false", touchscreen is optional
                if ("false".equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }
}