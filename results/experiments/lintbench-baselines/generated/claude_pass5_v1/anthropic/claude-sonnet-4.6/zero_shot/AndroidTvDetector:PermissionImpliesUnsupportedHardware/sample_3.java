package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Detector for TV-related manifest issues.
 *
 * <p>Checks that permissions which imply hardware features have a corresponding
 * {@code <uses-feature>} element with {@code required="false"} when targeting TV.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    /** Permission implies unsupported hardware feature */
    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies "
                            + "an unsupported TV hardware feature. Google Play assumes that certain "
                            + "hardware related permissions indicate that the underlying hardware "
                            + "features are required by default. To fix the issue, consider declaring "
                            + "the corresponding `uses-feature` element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    // Mapping from permission name to the hardware feature it implies
    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    static {
        PERMISSION_TO_FEATURE.put(
                "android.permission.BLUETOOTH",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put(
                "android.permission.BLUETOOTH_ADMIN",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CALL_PHONE",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CALL_PRIVILEGED",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CAMERA",
                "android.hardware.camera");
        PERMISSION_TO_FEATURE.put(
                "android.permission.FLASHLIGHT",
                "android.hardware.camera.flash");
        PERMISSION_TO_FEATURE.put(
                "android.permission.INTERNET",
                null); // no implied feature
        PERMISSION_TO_FEATURE.put(
                "android.permission.MODIFY_AUDIO_SETTINGS",
                null);
        PERMISSION_TO_FEATURE.put(
                "android.permission.NFC",
                "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put(
                "android.permission.PROCESS_OUTGOING_CALLS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.READ_CALL_LOG",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.READ_CONTACTS",
                null);
        PERMISSION_TO_FEATURE.put(
                "android.permission.READ_PHONE_STATE",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.READ_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_MMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_WAP_PUSH",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECORD_AUDIO",
                "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put(
                "android.permission.SEND_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.USE_SIP",
                "android.hardware.sip.voip");
        PERMISSION_TO_FEATURE.put(
                "android.permission.WRITE_APN_SETTINGS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.WRITE_CALL_LOG",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.WRITE_CONTACTS",
                null);
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_COARSE_LOCATION",
                null);
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_FINE_LOCATION",
                null);
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_MOCK_LOCATION",
                null);
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
                null);
    }

    // The set of permissions that imply hardware features unsupported on TV
    // (i.e., only the ones with non-null features in PERMISSION_TO_FEATURE)
    private static final Map<String, String> PERMISSION_IMPLIES_HARDWARE;

    static {
        PERMISSION_IMPLIES_HARDWARE = new HashMap<>();
        for (Map.Entry<String, String> entry : PERMISSION_TO_FEATURE.entrySet()) {
            if (entry.getValue() != null) {
                PERMISSION_IMPLIES_HARDWARE.put(entry.getKey(), entry.getValue());
            }
        }
    }

    // Hardware features that are not supported on TV
    private static final Set<String> UNSUPPORTED_TV_HARDWARE = new HashSet<>(Arrays.asList(
            "android.hardware.bluetooth",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.capability.manual_post_processing",
            "android.hardware.camera.capability.manual_sensor",
            "android.hardware.camera.capability.raw",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.camera.level.full",
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
            "android.hardware.usb.host"
    ));

    private static final String ANDROID_MANIFEST_USES_FEATURE = "uses-feature";
    private static final String ANDROID_MANIFEST_USES_PERMISSION = "uses-permission";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String VALUE_FALSE = "false";
    private static final String USES_FEATURE_TV = "android.software.leanback";
    private static final String CATEGORY_TV = "android.intent.category.LEANBACK_LAUNCHER";

    public AndroidTvDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                ANDROID_MANIFEST_USES_PERMISSION,
                ANDROID_MANIFEST_USES_FEATURE
        );
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // We handle everything in visitElement
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String elementName = element.getLocalName();
        if (elementName == null) {
            elementName = element.getTagName();
        }

        if (ANDROID_MANIFEST_USES_PERMISSION.equals(elementName)) {
            checkUsesPermission(context, element);
        }
    }

    private void checkUsesPermission(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME);
        if (permission == null || permission.isEmpty()) {
            return;
        }

        // Check if this permission implies a hardware feature
        String impliedFeature = PERMISSION_IMPLIES_HARDWARE.get(permission);
        if (impliedFeature == null) {
            return;
        }

        // Check if this is a TV app
        if (!isTvApp(context)) {
            return;
        }

        // Check if the implied hardware feature is unsupported on TV
        if (!UNSUPPORTED_TV_HARDWARE.contains(impliedFeature)) {
            return;
        }

        // Check if there is a corresponding uses-feature element with required="false"
        if (hasUsesFeatureWithRequiredFalse(context, impliedFeature)) {
            return;
        }

        // Report the issue
        String message =
                "Permission `"
                        + permission
                        + "` implies feature `"
                        + impliedFeature
                        + "` which is not supported by TV. Consider declaring "
                        + "`<uses-feature android:name=\""
                        + impliedFeature
                        + "\" android:required=\"false\"/>` in your manifest.";

        Attr nameAttr = element.getAttributeNodeNS(ANDROID_NAMESPACE, ATTR_NAME);
        if (nameAttr != null) {
            context.report(
                    PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                    element,
                    context.getLocation(nameAttr),
                    message);
        } else {
            context.report(
                    PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                    element,
                    context.getLocation(element),
                    message);
        }
    }

    /**
     * Returns true if the manifest targets TV (has leanback uses-feature or leanback launcher
     * category in an intent-filter).
     */
    private boolean isTvApp(@NonNull XmlContext context) {
        Document document = context.document;
        if (document == null) {
            return false;
        }

        Element manifest = document.getDocumentElement();
        if (manifest == null) {
            return false;
        }

        // Check for <uses-feature android:name="android.software.leanback">
        NodeList usesFeatures = manifest.getElementsByTagName(ANDROID_MANIFEST_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String name = feature.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME);
            if (USES_FEATURE_TV.equals(name)) {
                return true;
            }
        }

        // Check for activities with LEANBACK_LAUNCHER category
        NodeList activities = manifest.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList intentFilters = activity.getElementsByTagName("intent-filter");
            for (int j = 0; j < intentFilters.getLength(); j++) {
                Element intentFilter = (Element) intentFilters.item(j);
                NodeList categories = intentFilter.getElementsByTagName("category");
                for (int k = 0; k < categories.getLength(); k++) {
                    Element category = (Element) categories.item(k);
                    String name = category.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME);
                    if (CATEGORY_TV.equals(name)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Returns true if the manifest has a {@code <uses-feature>} element for the given feature
     * name with {@code android:required="false"}.
     */
    private boolean hasUsesFeatureWithRequiredFalse(
            @NonNull XmlContext context, @NonNull String featureName) {
        Document document = context.document;
        if (document == null) {
            return false;
        }

        Element manifest = document.getDocumentElement();
        if (manifest == null) {
            return false;
        }

        NodeList usesFeatures = manifest.getElementsByTagName(ANDROID_MANIFEST_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String name = feature.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME);
            if (featureName.equals(name)) {
                String required = feature.getAttributeNS(ANDROID_NAMESPACE, ATTR_REQUIRED);
                if (VALUE_FALSE.equals(required)) {
                    return true;
                }
            }
        }

        return false;
    }
}