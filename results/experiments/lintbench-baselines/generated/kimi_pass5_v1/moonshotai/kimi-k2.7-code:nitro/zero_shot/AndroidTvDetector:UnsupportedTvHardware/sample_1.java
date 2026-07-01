package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.util.Collection;
import java.util.Collections;

import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String USES_FEATURE = "uses-feature";

    private static final String[] UNSUPPORTED_TV_FEATURES = {
            "android.hardware.camera",
            "android.hardware.nfc",
            "android.hardware.sensor",
            "android.hardware.telephony",
            "android.hardware.touchscreen",
    };

    private static final Implementation IMPLEMENTATION = new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV hardware feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                    + "Any `<uses-feature>` not explicitly marked with `required=\"false\"` is "
                    + "necessary on the device in order to be installed. Ensure that any features "
                    + "that might prevent it from being installed on a TV device are reviewed and "
                    + "marked as not required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty() || !isUnsupportedTvFeature(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if (!VALUE_FALSE.equals(required)) {
            String message = String.format(
                    "The `<uses-feature>` element should not require the unsupported TV hardware feature `%1$s`.",
                    name);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static boolean isUnsupportedTvFeature(String name) {
        for (String feature : UNSUPPORTED_TV_FEATURES) {
            if (name.equals(feature) || name.startsWith(feature + ".")) {
                return true;
            }
        }
        return false;
    }
}