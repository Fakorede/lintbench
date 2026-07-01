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
 * <p>Apps require the {@code android.hardware.touchscreen} feature by default. If you want your
 * app to be available on TV, you must explicitly declare that a touchscreen is not required.
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
    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";

    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you want"
                            + " your app to be available on TV, you must also explicitly declare"
                            + " that a touchscreen is not required as follows:\n"
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html");

    /** Whether this manifest targets Android TV (has LEANBACK_LAUNCHER category). */
    private boolean mHasLeanbackLauncher;

    /** Whether the manifest explicitly declares touchscreen as not required. */
    private boolean mHasTouchscreenNotRequired;

    /** The manifest element to report the issue on if needed. */
    private Element mManifestElement;

    public AndroidTvDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_USES_FEATURE,
                TAG_CATEGORY,
                "manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if ("manifest".equals(tagName)) {
            mManifestElement = element;
            // Reset state for each manifest visit
            mHasLeanbackLauncher = false;
            mHasTouchscreenNotRequired = false;
            return;
        }

        if (TAG_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (HARDWARE_TOUCHSCREEN.equals(name)) {
                String required = element.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "required");
                // If required is explicitly set to "false", touchscreen is optional
                if ("false".equals(required)) {
                    mHasTouchscreenNotRequired = true;
                }
            }
            return;
        }

        if (TAG_CATEGORY.equals(tagName)) {
            String name = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        // After the full document has been visited, check for issues
        if (mHasLeanbackLauncher && !mHasTouchscreenNotRequired) {
            // Re-scan the document to find the leanback launcher category element to report on
            Element reportElement = findLeanbackCategoryElement(document);
            if (reportElement != null) {
                context.report(
                        IMPLIED_TOUCHSCREEN_HARDWARE,
                        reportElement,
                        context.getLocation(reportElement),
                        "You must explicitly declare that a touchscreen is not required by adding"
                                + " `<uses-feature android:name=\"android.hardware.touchscreen\""
                                + " android:required=\"false\"/>` in your manifest");
            } else if (mManifestElement != null) {
                context.report(
                        IMPLIED_TOUCHSCREEN_HARDWARE,
                        mManifestElement,
                        context.getLocation(mManifestElement),
                        "You must explicitly declare that a touchscreen is not required by adding"
                                + " `<uses-feature android:name=\"android.hardware.touchscreen\""
                                + " android:required=\"false\"/>` in your manifest");
            }
        }
    }

    /**
     * Searches the document for the element with category LEANBACK_LAUNCHER to use as the
     * error reporting location.
     */
    private static Element findLeanbackCategoryElement(org.w3c.dom.Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return null;
        }
        return findLeanbackCategoryInElement(root);
    }

    private static Element findLeanbackCategoryInElement(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_CATEGORY.equals(childElement.getTagName())) {
                String name = childElement.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "name");
                if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    return childElement;
                }
            }
            Element found = findLeanbackCategoryInElement(childElement);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}