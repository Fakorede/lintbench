package com.android.tools.lint.checks;

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
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Set<String> UNSUPPORTED_TV_FEATURES;
    static {
        Set<String> features = new HashSet<>();
        features.add("android.hardware.telephony");
        features.add("android.hardware.touchscreen");
        features.add("android.hardware.faketouch");
        features.add("android.hardware.camera");
        features.add("android.hardware.nfc");
        features.add("android.hardware.location.gps");
        UNSUPPORTED_TV_FEATURES = Collections.unmodifiableSet(features);
    }

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV hardware feature required",
            "The `<uses-feature>` element should not require hardware features that are not "
                    + "supported on TV devices. Any feature not explicitly marked with "
                    + "`required=\"false\"` is treated as required and may prevent the app "
                    + "from being installed on TV devices.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty() || !UNSUPPORTED_TV_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, "required");
        if (required == null || required.isEmpty() || "true".equals(required)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Hardware feature `%1$s` is not supported on TV devices and should "
                                    + "be marked with `required=\"false\"`",
                            name));
        }
    }
}