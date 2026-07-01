package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String HARDWARE_FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";

    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. "
                            + "If you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as follows:\n"
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    /** Whether we have seen an explicit touchscreen uses-feature declaration */
    private boolean mSeenTouchscreenFeature;

    /** Whether we need to check (i.e., the manifest targets TV) */
    private boolean mNeedsCheck;

    /** The manifest element to report the issue on if needed */
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mSeenTouchscreenFeature = false;
        mNeedsCheck = false;
        mManifestElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (HARDWARE_FEATURE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            // If required is explicitly set to false, touchscreen is optional - that's correct
            if ("false".equals(required)) {
                mSeenTouchscreenFeature = true;
            } else {
                // required is either not set (defaults to true) or explicitly true
                // This means touchscreen is required - not optional
                // We still note we've seen it, but it's required
                mNeedsCheck = true;
                mManifestElement = element;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mSeenTouchscreenFeature) {
            XmlContext xmlContext = (XmlContext) context;
            // Check if this manifest has a uses-feature for TV category
            // We need to report the issue if touchscreen is not explicitly declared as not required
            // Look for the manifest document to get a location
            org.w3c.dom.Document document = xmlContext.document;
            if (document != null) {
                Element root = document.getDocumentElement();
                if (root != null) {
                    // Check if any uses-feature or intent-filter indicates TV
                    boolean hasLeanbackFeature = false;
                    boolean hasLeanbackLauncher = false;

                    org.w3c.dom.NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        org.w3c.dom.Node child = children.item(i);
                        if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                            continue;
                        }
                        Element childElement = (Element) child;
                        String tagName = childElement.getTagName();

                        if (NODE_USES_FEATURE.equals(tagName)) {
                            String featureName = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                            if ("android.software.leanback".equals(featureName)) {
                                hasLeanbackFeature = true;
                            }
                        } else if ("application".equals(tagName)) {
                            // Look for leanback launcher intent-filter inside activities
                            hasLeanbackLauncher = hasLeanbackLauncherActivity(childElement);
                        }
                    }

                    if (hasLeanbackFeature || hasLeanbackLauncher) {
                        xmlContext.report(
                                IMPLIED_TOUCHSCREEN_HARDWARE,
                                root,
                                xmlContext.getLocation(root),
                                "You must explicitly declare that the `android.hardware.touchscreen`"
                                        + " feature is not required if you want your app to be"
                                        + " available on TV. Add"
                                        + " `<uses-feature"
                                        + " android:name=\"android.hardware.touchscreen\""
                                        + " android:required=\"false\"/>`"
                                        + " to your manifest.");
                    }
                }
            }
        }
    }

    private boolean hasLeanbackLauncherActivity(@NonNull Element application) {
        org.w3c.dom.NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if ("activity".equals(childElement.getTagName())
                    || "activity-alias".equals(childElement.getTagName())) {
                if (hasLeanbackLauncherIntentFilter(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackLauncherIntentFilter(@NonNull Element activity) {
        org.w3c.dom.NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if ("intent-filter".equals(childElement.getTagName())) {
                if (intentFilterHasLeanbackLauncher(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilter) {
        boolean hasLeanbackCategory = false;
        boolean hasMainAction = false;

        org.w3c.dom.NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();
            String nameAttr = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);

            if ("category".equals(tagName)
                    && "android.intent.category.LEANBACK_LAUNCHER".equals(nameAttr)) {
                hasLeanbackCategory = true;
            } else if ("action".equals(tagName)
                    && "android.intent.action.MAIN".equals(nameAttr)) {
                hasMainAction = true;
            }
        }

        return hasLeanbackCategory && hasMainAction;
    }
}