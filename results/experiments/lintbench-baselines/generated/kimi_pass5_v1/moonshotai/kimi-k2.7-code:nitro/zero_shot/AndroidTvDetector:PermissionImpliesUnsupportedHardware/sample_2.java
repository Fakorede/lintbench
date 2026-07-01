package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE = Issue.create(
        "PermissionImpliesUnsupportedHardware",
        "Permission Implies Unsupported Hardware",
        "The <code>&lt;uses-permission&gt;</code> element should not require a permission that "
            + "implies an unsupported TV hardware feature. Google Play assumes that certain "
            + "hardware related permissions indicate that the underlying hardware features are "
            + "required by default. To fix the issue, declare the corresponding "
            + "<code>&lt;uses-feature&gt;</code> element with "
            + "<code>android:required=\"false\"</code>.<br><br>"
            + "See <a href=\"https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions\">"
            + "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions</a>.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Map<String, List<String>> PERMISSION_TO_FEATURES;
    static {
        Map<String, List<String>> map = new HashMap<>();
        addPermissionFeatures(map, "android.permission.CAMERA",
            "android.hardware.camera",
            "android.hardware.camera.autofocus");
        addPermissionFeatures(map, "android.permission.CALL_PHONE",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.CALL_PRIVILEGED",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.MODIFY_PHONE_STATE",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.PROCESS_OUTGOING_CALLS",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.READ_PHONE_STATE",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.READ_SMS",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.RECEIVE_SMS",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.RECEIVE_WAP_PUSH",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.SEND_SMS",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.WRITE_APN_SETTINGS",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.WRITE_SMS",
            "android.hardware.telephony");
        addPermissionFeatures(map, "android.permission.ACCESS_COARSE_LOCATION",
            "android.hardware.location");
        addPermissionFeatures(map, "android.permission.ACCESS_FINE_LOCATION",
            "android.hardware.location",
            "android.hardware.location.gps");
        addPermissionFeatures(map, "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
            "android.hardware.location");
        addPermissionFeatures(map, "android.permission.CONTROL_LOCATION_UPDATES",
            "android.hardware.location");
        addPermissionFeatures(map, "android.permission.INSTALL_LOCATION_PROVIDER",
            "android.hardware.location");
        addPermissionFeatures(map, "android.permission.RECORD_AUDIO",
            "android.hardware.microphone");
        addPermissionFeatures(map, "android.permission.BLUETOOTH",
            "android.hardware.bluetooth");
        addPermissionFeatures(map, "android.permission.BLUETOOTH_ADMIN",
            "android.hardware.bluetooth");
        addPermissionFeatures(map, "android.permission.BLUETOOTH_PRIVILEGED",
            "android.hardware.bluetooth");
        addPermissionFeatures(map, "android.permission.NFC",
            "android.hardware.nfc");
        addPermissionFeatures(map, "android.permission.TRANSMIT_IR",
            "android.hardware.consumerir");
        addPermissionFeatures(map, "android.permission.UWB_RANGING",
            "android.hardware.uwb");
        PERMISSION_TO_FEATURES = Collections.unmodifiableMap(map);
    }

    private static void addPermissionFeatures(Map<String, List<String>> map,
            String permission, String... features) {
        List<String> list = new ArrayList<>(features.length);
        Collections.addAll(list, features);
        map.put(permission, Collections.unmodifiableList(list));
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (permission.isEmpty()) {
            return;
        }

        List<String> features = PERMISSION_TO_FEATURES.get(permission);
        if (features == null) {
            return;
        }

        Element manifest = element.getOwnerDocument().getDocumentElement();
        for (String feature : features) {
            if (!hasFeatureNotRequired(manifest, feature)) {
                String message = String.format(
                    "Permission `%1$s` implies feature `%2$s`, which is not supported on "
                        + "Android TV. Consider adding "
                        + "`<uses-feature android:name=\"%2$s\" android:required=\"false\" />`.",
                    permission, feature);
                context.report(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE, element,
                    context.getLocation(element), message);
            }
        }
    }

    private static boolean hasFeatureNotRequired(@NonNull Element manifest,
            @NonNull String feature) {
        NodeList children = manifest.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_USES_FEATURE.equals(child.getNodeName())) {
                Element featureElement = (Element) child;
                String name = featureElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (feature.equals(name)
                        && VALUE_FALSE.equals(
                            featureElement.getAttributeNS(ANDROID_URI, ATTR_REQUIRED))) {
                    return true;
                }
            }
        }
        return false;
    }
}