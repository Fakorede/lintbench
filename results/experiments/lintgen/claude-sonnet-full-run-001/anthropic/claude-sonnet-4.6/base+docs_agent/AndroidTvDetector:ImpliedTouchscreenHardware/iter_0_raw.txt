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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

/**
 * Detector for the ImpliedTouchscreenHardware issue.
 *
 * <p>Apps require the {@code android.hardware.touchscreen} feature by default. If you want your
 * app to be available on TV, you must also explicitly declare that a touchscreen is not required.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APPLICATION = "application";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. "
                            + "If you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as follows:\n"
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

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Only check AndroidManifest.xml files
        if (!ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        // Check if this app targets TV (has LEANBACK_LAUNCHER category)
        if (!isLeanbackApp(document)) {
            return;
        }

        // Check if touchscreen is explicitly declared as not required
        if (!isTouchscreenOptional(document)) {
            // Find a good location to report the error
            Element manifestElement = document.getDocumentElement();
            context.report(
                    ISSUE,
                    manifestElement,
                    context.getLocation(manifestElement),
                    "Touchscreen not optional: apps require the "
                            + "`android.hardware.touchscreen` feature by default. "
                            + "If you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as follows: "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`");
        }
    }

    /**
     * Returns true if the manifest contains a LEANBACK_LAUNCHER category, indicating this app
     * targets Android TV.
     */
    private static boolean isLeanbackApp(Document document) {
        NodeList activities = document.getElementsByTagName(TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER);
            for (int j = 0; j < intentFilters.getLength(); j++) {
                Element intentFilter = (Element) intentFilters.item(j);
                NodeList categories = intentFilter.getElementsByTagName(TAG_CATEGORY);
                for (int k = 0; k < categories.getLength(); k++) {
                    Element category = (Element) categories.item(k);
                    String name = category.getAttributeNS(
                            "http://schemas.android.com/apk/res/android", "name");
                    if (name == null || name.isEmpty()) {
                        name = category.getAttribute(ATTR_NAME);
                    }
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the manifest explicitly declares the touchscreen feature as not required.
     */
    private static boolean isTouchscreenOptional(Document document) {
        NodeList usesFeatures = document.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element usesFeature = (Element) usesFeatures.item(i);
            String name = usesFeature.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = usesFeature.getAttribute(ATTR_NAME);
            }
            if (FEATURE_TOUCHSCREEN.equals(name)) {
                String required = usesFeature.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "required");
                if (required == null || required.isEmpty()) {
                    required = usesFeature.getAttribute(ATTR_REQUIRED);
                }
                if ("false".equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }
}