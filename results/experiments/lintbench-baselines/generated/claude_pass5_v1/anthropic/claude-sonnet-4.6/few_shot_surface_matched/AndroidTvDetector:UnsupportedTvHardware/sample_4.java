package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;

import com.android.annotations.NonNull;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue UNSUPPORTED_TV_HARDWARE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware "
                            + "feature. Any uses-feature not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to be installed on. "
                            + "Ensure that any features that might prevent it from being installed "
                            + "on a TV device are reviewed and marked as not required in the "
                            + "manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    /** Hardware features not supported on TV */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES =
            new HashSet<>(
                    Arrays.asList(
                            "android.hardware.bluetooth",
                            "android.hardware.camera",
                            "android.hardware.camera.autofocus",
                            "android.hardware.camera.capability.manual_post_processing",
                            "android.hardware.camera.capability.manual_sensor",
                            "android.hardware.camera.capability.raw",
                            "android.hardware.camera.flash",
                            "android.hardware.camera.front",
                            "android.hardware.camera.any",
                            "android.hardware.camera.level.full",
                            "android.hardware.consumerir",
                            "android.hardware.location.gps",
                            "android.hardware.microphone",
                            "android.hardware.nfc",
                            "android.hardware.nfc.hce",
                            "android.hardware.sensor.accelerometer",
                            "android.hardware.sensor.barometer",
                            "android.hardware.sensor.compass",
                            "android.hardware.sensor.gyroscope",
                            "android.hardware.sensor.light",
                            "android.hardware.sensor.proximity",
                            "android.hardware.sensor.stepcounter",
                            "android.hardware.sensor.stepdetector",
                            "android.hardware.telephony",
                            "android.hardware.telephony.cdma",
                            "android.hardware.telephony.gsm",
                            "android.hardware.touchscreen",
                            "android.hardware.touchscreen.multitouch",
                            "android.hardware.touchscreen.multitouch.distinct",
                            "android.hardware.touchscreen.multitouch.jazzhand",
                            "android.hardware.usb.accessory",
                            "android.hardware.usb.host",
                            "android.hardware.wifi"
                    ));

    /** Whether the manifest targets TV (has a TV launcher intent-filter category) */
    private boolean mTargetsTv;

    /** The element we want to report after we've determined if the app targets TV */
    private final Set<Element> mUnsupportedHardwareElements = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mTargetsTv = false;
        mUnsupportedHardwareElements.clear();
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mTargetsTv && !mUnsupportedHardwareElements.isEmpty()) {
            XmlContext xmlContext = (XmlContext) context;
            for (Element element : mUnsupportedHardwareElements) {
                Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                String featureName = nameAttr != null ? nameAttr.getValue() : "";
                xmlContext.report(
                        UNSUPPORTED_TV_HARDWARE,
                        element,
                        xmlContext.getLocation(element),
                        "Unsupported TV hardware feature `" + featureName + "`");
            }
        }
        mUnsupportedHardwareElements.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if the manifest targets TV by looking for the LEANBACK_LAUNCHER category
        // We need to check the whole document for this
        if (!mTargetsTv) {
            mTargetsTv = checkTargetsTv(element);
        }

        // Check if this uses-feature is an unsupported TV hardware feature
        String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if required is explicitly set to false
        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            return;
        }

        mUnsupportedHardwareElements.add(element);
    }

    /**
     * Checks whether the manifest targets TV by looking for
     * android.intent.category.LEANBACK_LAUNCHER in any intent-filter.
     */
    private boolean checkTargetsTv(@NonNull Element element) {
        // Walk up to document root and then search
        Node root = element;
        while (root.getParentNode() != null && root.getParentNode().getNodeType() == Node.ELEMENT_NODE) {
            root = root.getParentNode();
        }
        return elementContainsLeanbackLauncher((Element) root);
    }

    private boolean elementContainsLeanbackLauncher(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();
            if (NODE_CATEGORY.equals(tagName)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    return true;
                }
            }
            if (elementContainsLeanbackLauncher(childElement)) {
                return true;
            }
        }
        return false;
    }
}