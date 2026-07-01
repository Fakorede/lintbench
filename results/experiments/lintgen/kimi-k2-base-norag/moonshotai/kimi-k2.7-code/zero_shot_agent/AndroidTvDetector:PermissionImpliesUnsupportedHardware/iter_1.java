package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.FEATURE_CAMERA;
import static com.android.SdkConstants.FEATURE_CAMERA_AUTOFOCUS;
import static com.android.SdkConstants.FEATURE_LOCATION;
import static com.android.SdkConstants.FEATURE_LOCATION_GPS;
import static com.android.SdkConstants.FEATURE_LOCATION_NETWORK;
import static com.android.SdkConstants.FEATURE_MICROPHONE;
import static com.android.SdkConstants.FEATURE_SENSOR_HEART_RATE;
import static com.android.SdkConstants.FEATURE_TELEPHONY;
import static com.android.SdkConstants.PERMISSION_ACCESS_COARSE_LOCATION;
import static com.android.SdkConstants.PERMISSION_ACCESS_FINE_LOCATION;
import static com.android.SdkConstants.PERMISSION_BODY_SENSORS;
import static com.android.SdkConstants.PERMISSION_CALL_PHONE;
import static com.android.SdkConstants.PERMISSION_CAMERA;
import static com.android.SdkConstants.PERMISSION_READ_PHONE_STATE;
import static com.android.SdkConstants.PERMISSION_RECEIVE_MMS;
import static com.android.SdkConstants.PERMISSION_RECEIVE_SMS;
import static com.android.SdkConstants.PERMISSION_RECEIVE_WAP_PUSH;
import static com.android.SdkConstants.PERMISSION_RECORD_AUDIO;
import static com.android.SdkConstants.PERMISSION_SEND_SMS;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final Map<String, List<String>> PERMISSION_TO_FEATURES;
    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put(PERMISSION_CAMERA,
                Arrays.asList(FEATURE_CAMERA, FEATURE_CAMERA_AUTOFOCUS));
        map.put(PERMISSION_RECORD_AUDIO,
                Collections.singletonList(FEATURE_MICROPHONE));
        map.put(PERMISSION_ACCESS_FINE_LOCATION,
                Arrays.asList(FEATURE_LOCATION, FEATURE_LOCATION_GPS));
        map.put(PERMISSION_ACCESS_COARSE_LOCATION,
                Arrays.asList(FEATURE_LOCATION, FEATURE_LOCATION_NETWORK));
        map.put(PERMISSION_CALL_PHONE,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_READ_PHONE_STATE,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_SEND_SMS,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_RECEIVE_SMS,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_READ_SMS,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_RECEIVE_WAP_PUSH,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_RECEIVE_MMS,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_BODY_SENSORS,
                Collections.singletonList(FEATURE_SENSOR_HEART_RATE));
        PERMISSION_TO_FEATURES = Collections.unmodifiableMap(map);
    }

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware",
            "The `<uses-permission>` element should not require a permission that implies "
                    + "an unsupported TV hardware feature. Google Play assumes that certain "
                    + "hardware related permissions indicate that the underlying hardware "
                    + "features are required by default. To fix the issue, consider declaring "
                    + "the corresponding `<uses-feature>` element with "
                    + "`android:required=\"false\"`.",
            Category.TV,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    ...
}