package com.android.tools.lint.checks;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String TAG_MANIFEST = "manifest";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that "
                            + "implies an unsupported TV hardware feature. Google Play assumes "
                            + "that certain hardware related permissions indicate that the "
                            + "underlying hardware features are required by default. To fix "
                            + "the issue, consider declaring the corresponding `uses-feature` "
                            + "element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /**
     * Map from permission name to the hardware feature it implies.
     * Based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        map.put("android.permission.CAMERA", "android.hardware.camera");
        map.put("android.permission.CHANGE_WIFI_MULTICAST_STATE", "android.hardware.wifi");
        map.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");
        map.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        map.put("android.permission.NFC", "android.hardware.nfc");
        map.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        map.put("android.permission.USE_SIP", "android.hardware.sip.voip");
        map.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        map.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        map.put("android.permission.ACCESS_MOCK_LOCATION", "android.hardware.location");
        map.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", "android.hardware.location");
        map.put("android.permission.INSTALL_LOCATION_PROVIDER", "android.hardware.location");
        map.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        map.put("android.permission.READ_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        map.put("android.permission.SEND_SMS", "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS", "android.hardware.telephony");
        map.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    /** Set of features that are unsupported on TV */
    private static final Set<String> UNSUPPORTED_TV_FEATURES =
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
                            "android.hardware.camera.level.full",
                            "android.hardware.location",
                            "android.hardware.location.gps",
                            "android.hardware.location.network",
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
                            "android.hardware.wifi",
                            "android.hardware.sip.voip",
                            "android.hardware.usb.accessory",
                            "android.hardware.usb.host"));

    /** Permissions seen in the manifest during the current file check */
    private Set<String> mPermissionsFound;

    /** Features declared with required="false" in the manifest during the current file check */
    private Set<String> mFeaturesNotRequired;

    /** Whether we are currently checking a TV-targeted manifest */
    private boolean mIsTvManifest;

    /** Element nodes for uses-permission tags, for reporting issues */
    private Map<String, Element> mPermissionElements;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissionsFound = new HashSet<>();
        mFeaturesNotRequired = new HashSet<>();
        mIsTvManifest = false;
        mPermissionElements = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsTvManifest) {
            return;
        }

        if (mPermissionsFound.isEmpty()) {
            return;
        }

        // For each permission found, check if it implies an unsupported hardware feature
        // and that feature is not already declared as not required
        for (String permission : mPermissionsFound) {
            String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
            if (impliedFeature != null
                    && UNSUPPORTED_TV_FEATURES.contains(impliedFeature)
                    && !mFeaturesNotRequired.contains(impliedFeature)) {
                Element permissionElement = mPermissionElements.get(permission);
                if (permissionElement != null && context instanceof XmlContext) {
                    XmlContext xmlContext = (XmlContext) context;
                    xmlContext.report(
                            ISSUE,
                            permissionElement,
                            xmlContext.getLocation(permissionElement),
                            String.format(
                                    "Permission `%1$s` implies that the TV device must support `%2$s`. "
                                            + "Consider declaring `<uses-feature android:name=\"%2$s\" "
                                            + "android:required=\"false\">` in your manifest.",
                                    permission,
                                    impliedFeature));
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        // Check if this manifest targets TV by looking for TV uses-feature or intent-filter
        // We detect TV manifest by checking if there's a LEANBACK_LAUNCHER or
        // uses-feature android.software.leanback
        // We'll do a broader check - look at the root manifest element for TV indicators
        if (!mIsTvManifest) {
            mIsTvManifest = isTvManifest(element);
        }

        if (TAG_USES_PERMISSION.equals(tagName)) {
            String name = getAttributeValue(element, ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                mPermissionsFound.add(name);
                mPermissionElements.put(name, element);
            }
        } else if (TAG_USES_FEATURE.equals(tagName)) {
            String name = getAttributeValue(element, ATTR_NAME);
            String required = getAttributeValue(element, ATTR_REQUIRED);
            if (name != null && !name.isEmpty()) {
                // If required is explicitly set to false, record it
                if ("false".equals(required)) {
                    mFeaturesNotRequired.add(name);
                }
                // Check for TV manifest indicator
                if ("android.software.leanback".equals(name)) {
                    mIsTvManifest = true;
                }
            }
        }
    }

    /**
     * Checks whether the manifest element implies this is a TV-targeted application.
     * We detect this by looking for uses-feature android.software.leanback in the document.
     */
    private boolean isTvManifest(@NonNull Element element) {
        // Check this element and its siblings/ancestors for TV indicators
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null) {
            return false;
        }

        // Look for uses-feature android.software.leanback
        NodeList usesFeatures = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String name = getAttributeValue(feature, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                return true;
            }
        }

        // Look for intent-filter with category LEANBACK_LAUNCHER
        NodeList intentFilters = root.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList categories = intentFilter.getElementsByTagName("category");
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = getAttributeValue(category, ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets the value of an attribute, checking both with and without namespace prefix.
     */
    private static String getAttributeValue(@NonNull Element element, @NonNull String attrName) {
        // Try with "android:" prefix first (as used in our constants)
        if (attrName.startsWith("android:")) {
            String localName = attrName.substring("android:".length());
            // Try namespace URI
            String value = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", localName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            // Try with prefix
            value = element.getAttribute(attrName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            // Try without prefix
            return element.getAttribute(localName);
        }
        return element.getAttribute(attrName);
    }
}