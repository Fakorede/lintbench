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

import java.util.Arrays;
import java.util.Collection;

/**
 * Detector for the ImpliedTouchscreenHardware issue.
 *
 * <p>Apps require the {@code android.hardware.touchscreen} feature by default. If you want your
 * app to be available on TV, you must also explicitly declare that a touchscreen is not required.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";

    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";

    private static final String TV_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APPLICATION = "application";

    public static final Issue ISSUE = Issue.create(
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
    ).addMoreInfo(
            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html"
    );

    /** Whether the manifest targets TV (has a LEANBACK_LAUNCHER category). */
    private boolean mTargetsTv = false;

    /** Whether the manifest explicitly declares touchscreen as not required. */
    private boolean mDeclaresTouchscreenNotRequired = false;

    /** The root manifest element, used to report the issue location if needed. */
    private Element mManifestElement = null;

    public AndroidTvDetector() {
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_USES_FEATURE,
                TAG_CATEGORY,
                "manifest"
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if ("manifest".equals(tagName)) {
            mManifestElement = element;
            return;
        }

        if (TAG_USES_FEATURE.equals(tagName)) {
            handleUsesFeature(element);
            return;
        }

        if (TAG_CATEGORY.equals(tagName)) {
            handleCategory(element);
        }
    }

    private void handleUsesFeature(Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            // Try without namespace
            name = element.getAttribute("android:name");
        }

        if (HARDWARE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "required");
            if (required == null || required.isEmpty()) {
                required = element.getAttribute("android:required");
            }
            // If required is explicitly set to "false", touchscreen is optional
            if ("false".equals(required)) {
                mDeclaresTouchscreenNotRequired = true;
            }
        }
    }

    private void handleCategory(Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (TV_CATEGORY.equals(name)) {
            mTargetsTv = true;
        }
    }

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        // Reset state for each document
        mTargetsTv = false;
        mDeclaresTouchscreenNotRequired = false;
        mManifestElement = null;
    }

    /**
     * Called after the document has been fully visited.
     */
    @Override
    public void afterCheckFile(com.android.tools.lint.detector.api.Context context) {
        if (mTargetsTv && !mDeclaresTouchscreenNotRequired) {
            XmlContext xmlContext = (XmlContext) context;
            org.w3c.dom.Document document = xmlContext.document;
            if (document != null) {
                Element root = document.getDocumentElement();
                if (root != null) {
                    xmlContext.report(
                            ISSUE,
                            root,
                            xmlContext.getLocation(root),
                            "Hardware feature `android.hardware.touchscreen` not explicitly " +
                            "marked as optional; this means your app will not be available on " +
                            "TV. You must declare `<uses-feature " +
                            "android:name=\"android.hardware.touchscreen\" " +
                            "android:required=\"false\"/>` in your manifest to be available on TV."
                    );
                }
            }
        }
    }
}