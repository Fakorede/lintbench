package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

import java.util.Arrays;
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
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
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
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>`",
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
        return Arrays.asList(TAG_USES_FEATURE, TAG_CATEGORY);
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
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>`");
        }
    }

    /**
     * Returns true if the manifest declares a LEANBACK_LAUNCHER category, indicating this is a TV
     * app.
     */
    private static boolean isLeanbackApp(Document document) {
        NodeList categories = document.getElementsByTagName(TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Element category = (Element) categories.item(i);
            String name = getAndroidAttribute(category, ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the manifest explicitly declares
     * {@code android.hardware.touchscreen} with {@code android:required="false"}.
     */
    private static boolean isTouchscreenOptional(Document document) {
        NodeList usesFeatures = document.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element usesFeature = (Element) usesFeatures.item(i);

            String name = getAndroidAttribute(usesFeature, ATTR_NAME);

            if (FEATURE_TOUCHSCREEN.equals(name)) {
                String required = getAndroidAttribute(usesFeature, ATTR_REQUIRED);
                if ("false".equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets an attribute value using the Android namespace first, falling back to no namespace.
     */
    private static String getAndroidAttribute(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_NS, localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        if (value == null || value.isEmpty()) {
            value = element.getAttribute(localName);
        }
        return value;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Handled in visitDocument
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return false;
    }
}