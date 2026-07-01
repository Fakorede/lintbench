package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.android.xml.AndroidManifest;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies an " +
            "unsupported TV hardware feature. Google Play assumes that certain hardware related " +
            "permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with " +
            "`required=\"false\"` attribute.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    ).addMoreInfo("https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    private static final String ANDROID_PERMISSION_CAMERA = "android.permission.CAMERA";
    private static final String ANDROID_PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO";
    private static final String ANDROID_PERMISSION_READ_CONTACTS = "android.permission.READ_CONTACTS";
    private static final String ANDROID_PERMISSION_WRITE_CONTACTS = "android.permission.WRITE_CONTACTS";
    private static final String ANDROID_PERMISSION_GET_ACCOUNTS = "android.permission.GET_ACCOUNTS";
    private static final String ANDROID_PERMISSION_CALL_PHONE = "android.permission.CALL_PHONE";
    private static final String ANDROID_PERMISSION_CALL_PRIVILEGED = "android.permission.CALL_PRIVILEGED";
    private static final String ANDROID_PERMISSION_PROCESS_OUTGOING_CALLS = "android.permission.PROCESS_OUTGOING_CALLS";
    private static final String ANDROID_PERMISSION_READ_CALL_LOG = "android.permission.READ_CALL_LOG";
    private static final String ANDROID_PERMISSION_WRITE_CALL_LOG = "android.permission.WRITE_CALL_LOG";
    private static final String ANDROID_PERMISSION_ADD_VOICEMAIL = "com.android.voicemail.permission.ADD_VOICEMAIL";
    private static final String ANDROID_PERMISSION_USE_SIP = "android.permission.USE_SIP";
    private static final String ANDROID_PERMISSION_SEND_SMS = "android.permission.SEND_SMS";
    private static final String ANDROID_PERMISSION_RECEIVE_SMS = "android.permission.RECEIVE_SMS";
    private static final String ANDROID_PERMISSION_READ_SMS = "android.permission.READ_SMS";
    private static final String ANDROID_PERMISSION_RECEIVE_WAP_PUSH = "android.permission.RECEIVE_WAP_PUSH";
    private static final String ANDROID_PERMISSION_RECEIVE_MMS = "android.permission.RECEIVE_MMS";
    private static final String ANDROID_PERMISSION_SEND_RESPOND_VIA_MESSAGE = "android.permission.SEND_RESPOND_VIA_MESSAGE";
    private static final String ANDROID_PERMISSION_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE";
    private static final String ANDROID_PERMISSION_WRITE_APN_SETTINGS = "android.permission.WRITE_APN_SETTINGS";
    private static final String ANDROID_PERMISSION_CHANGE_NETWORK_STATE = "android.permission.CHANGE_NETWORK_STATE";
    private static final String ANDROID_PERMISSION_SUBSCRIBED_FEEDS_READ = "android.permission.SUBSCRIBED_FEEDS_READ";
    private static final String ANDROID_PERMISSION_SUBSCRIBED_FEEDS_WRITE = "android.permission.SUBSCRIBED_FEEDS_WRITE";
    private static final String ANDROID_PERMISSION_BLUETOOTH = "android.permission.BLUETOOTH";
    private static final String ANDROID_PERMISSION_BLUETOOTH_ADMIN = "android.permission.BLUETOOTH_ADMIN";
    private static final String ANDROID_PERMISSION_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
    private static final String ANDROID_PERMISSION_ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
    private static final String ANDROID_PERMISSION_ACCESS_MOCK_LOCATION = "android.permission.ACCESS_MOCK_LOCATION";
    private static final String ANDROID_PERMISSION_ACCESS_LOCATION_EXTRA_COMMANDS = "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS";
    private static final String ANDROID_PERMISSION_INSTALL_LOCATION_PROVIDER = "android.permission.INSTALL_LOCATION_PROVIDER";

    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_CAMERA_AUTOFOCUS = "android.hardware.camera.autofocus";
    private static final String FEATURE_CAMERA_FLASH = "android.hardware.camera.flash";
    private static final String FEATURE_MICROPHONE = "android.hardware.microphone";
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";
    private static final String FEATURE_BLUETOOTH = "android.hardware.bluetooth";
    private static final String FEATURE_LOCATION = "android.hardware.location";
    private static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    private static final String FEATURE_LOCATION_NETWORK = "android.hardware.location.network";
    private static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";

    /**
     * Map from permission name to the hardware feature it implies.
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put(ANDROID_PERMISSION_CAMERA, FEATURE_CAMERA);
        map.put(ANDROID_PERMISSION_RECORD_AUDIO, FEATURE_MICROPHONE);
        map.put(ANDROID_PERMISSION_READ_CONTACTS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_WRITE_CONTACTS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_GET_ACCOUNTS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_CALL_PHONE, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_CALL_PRIVILEGED, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_PROCESS_OUTGOING_CALLS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_READ_CALL_LOG, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_WRITE_CALL_LOG, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_ADD_VOICEMAIL, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_USE_SIP, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_SEND_SMS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_RECEIVE_SMS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_READ_SMS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_RECEIVE_WAP_PUSH, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_RECEIVE_MMS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_SEND_RESPOND_VIA_MESSAGE, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_READ_PHONE_STATE, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_WRITE_APN_SETTINGS, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_CHANGE_NETWORK_STATE, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_SUBSCRIBED_FEEDS_READ, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_SUBSCRIBED_FEEDS_WRITE, FEATURE_TELEPHONY);
        map.put(ANDROID_PERMISSION_BLUETOOTH, FEATURE_BLUETOOTH);
        map.put(ANDROID_PERMISSION_BLUETOOTH_ADMIN, FEATURE_BLUETOOTH);
        map.put(ANDROID_PERMISSION_ACCESS_FINE_LOCATION, FEATURE_LOCATION_GPS);
        map.put(ANDROID_PERMISSION_ACCESS_COARSE_LOCATION, FEATURE_LOCATION_NETWORK);
        map.put(ANDROID_PERMISSION_ACCESS_MOCK_LOCATION, FEATURE_LOCATION);
        map.put(ANDROID_PERMISSION_ACCESS_LOCATION_EXTRA_COMMANDS, FEATURE_LOCATION);
        map.put(ANDROID_PERMISSION_INSTALL_LOCATION_PROVIDER, FEATURE_LOCATION);
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    /**
     * Set of hardware features that are not supported on Android TV.
     */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES;

    static {
        Set<String> set = new HashSet<>();
        set.add(FEATURE_CAMERA);
        set.add(FEATURE_CAMERA_AUTOFOCUS);
        set.add(FEATURE_CAMERA_FLASH);
        set.add(FEATURE_MICROPHONE);
        set.add(FEATURE_TELEPHONY);
        set.add(FEATURE_BLUETOOTH);
        set.add(FEATURE_LOCATION);
        set.add(FEATURE_LOCATION_GPS);
        set.add(FEATURE_LOCATION_NETWORK);
        set.add(FEATURE_TOUCHSCREEN);
        UNSUPPORTED_TV_HARDWARE_FEATURES = Collections.unmodifiableSet(set);
    }

    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_TRUE = "true";
    private static final String VALUE_FALSE = "false";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_APPLICATION = "application";
    private static final String ATTR_USES_LEANBACK = "leanback";

    /**
     * Whether this manifest targets Android TV (has a uses-feature for leanback).
     */
    private boolean mTargetsTv;

    /**
     * Map from feature name to whether it is explicitly declared with required=false.
     * Key: feature name, Value: true if required=false, false if required=true or not specified.
     */
    private Map<String, Boolean> mDeclaredFeatures;

    /**
     * List of uses-permission elements that imply unsupported TV hardware.
     * Each entry: [permission name, implied feature name, element node]
     */
    private List<Object[]> mPermissionsToCheck;

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_USES_PERMISSION,
                TAG_USES_FEATURE
        );
    }

    @Override
    public void beforeCheckFile(Context context) {
        mTargetsTv = false;
        mDeclaredFeatures = new HashMap<>();
        mPermissionsToCheck = new ArrayList<>();
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mTargetsTv) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;

        for (Object[] entry : mPermissionsToCheck) {
            String permissionName = (String) entry[0];
            String impliedFeature = (String) entry[1];
            Element permissionElement = (Element) entry[2];

            if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
                continue;
            }

            // Check if the feature is declared with required=false
            Boolean declaredNotRequired = mDeclaredFeatures.get(impliedFeature);
            if (declaredNotRequired != null && declaredNotRequired) {
                // Feature is declared with required=false, no issue
                continue;
            }

            // Report the issue
            String message = String.format(
                    "Permission `%1$s` implies unsupported TV hardware feature `%2$s`",
                    permissionName,
                    impliedFeature
            );

            if (declaredNotRequired == null) {
                // Feature not declared at all
                message += String.format(
                        ". Consider adding `<uses-feature android:name=\"%1$s\" android:required=\"false\"/>` to the manifest.",
                        impliedFeature
                );
            } else {
                // Feature declared but with required=true
                message += String.format(
                        ". Consider changing the `<uses-feature android:name=\"%1$s\">` element to include `android:required=\"false\"`.",
                        impliedFeature
                );
            }

            xmlContext.report(
                    PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                    permissionElement,
                    xmlContext.getLocation(permissionElement),
                    message
            );
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if (TAG_USES_FEATURE.equals(tagName)) {
            handleUsesFeature(element);
        } else if (TAG_USES_PERMISSION.equals(tagName)) {
            handleUsesPermission(element);
        }

        // Check for leanback feature to determine if this targets TV
        if (TAG_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                mTargetsTv = true;
            }
        }
    }

    private void handleUsesFeature(Element element) {
        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String requiredAttr = element.getAttributeNS(ANDROID_NS, ATTR_REQUIRED);
        boolean requiredFalse = VALUE_FALSE.equals(requiredAttr);

        // Store: true means required=false (i.e., not required), false means required=true or not specified
        if (!mDeclaredFeatures.containsKey(name)) {
            mDeclaredFeatures.put(name, requiredFalse);
        } else {
            // If already declared, keep the most permissive (required=false wins)
            if (requiredFalse) {
                mDeclaredFeatures.put(name, true);
            }
        }
    }

    private void handleUsesPermission(Element element) {
        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String impliedFeature = PERMISSION_TO_FEATURE.get(name);
        if (impliedFeature == null) {
            return;
        }

        mPermissionsToCheck.add(new Object[]{name, impliedFeature, element});
    }

    @Override
    public List<Issue> getIssues() {
        return Collections.singletonList(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
    }
}