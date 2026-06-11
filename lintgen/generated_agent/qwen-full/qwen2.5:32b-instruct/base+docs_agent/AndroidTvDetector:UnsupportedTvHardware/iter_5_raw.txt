package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ScreenSize;
import com.android.utils.Pair;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.xmlpull.v1.XmlPullParser;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;

public class AndroidTvDetector extends Detector implements XmlScanner {
    private static final String USES_FEATURE = "uses-feature";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String REQUIRED_ATTRIBUTE = "required";

    // List of unsupported TV hardware features
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES = new HashSet<>(
            Set.of(
                    SdkConstants.FEATURE_BLUETOOTH,
                    SdkConstants.FEATURE_CAMERA_ANY,
                    SdkConstants.FEATURE_FINGERPRINT_HW,
                    SdkConstants.FEATURE_NFC,
                    SdkConstants.FEATURE_TELEPHONY
            )
    );

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(USES_FEATURE);
    }

    private static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardwareFeature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature.",
            "Any `uses-feature` not explicitly marked with `required=\"false\"` is necessary on the device to be installed on. Ensure that any features that might prevent it from being installed on a TV device are reviewed and marked as not required in the manifest.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameAttr = element.getAttributeNode(NAME_ATTRIBUTE);
        if (nameAttr != null && UNSUPPORTED_TV_HARDWARE_FEATURES.contains(nameAttr.getValue())) {
            Attr requiredAttr = element.getAttributeNode(REQUIRED_ATTRIBUTE);
            if (requiredAttr == null || !"false".equals(requiredAttr.getValue())) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Mark the feature as not required", new Fix() {
                            @Override
                            public void run(@NonNull Context context) throws Exception {
                                Attr attr = element.getAttributeNode(REQUIRED_ATTRIBUTE);
                                if (attr == null) {
                                    XmlUtils.setAttribute(context.getDriver().getXmlEditor(), REQUIRED_ATTRIBUTE, "false");
                                } else {
                                    attr.setValue("false");
                                }
                            }

                            @NonNull
                            @Override
                            public String getName() {
                                return "Mark the feature as not required";
                            }
                        });
            }
        }
    }
}