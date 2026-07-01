package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private Element mManifestElement;
    private boolean mHasOptionalTouchscreen;

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("manifest".equals(tag)) {
            mManifestElement = element;
            mHasOptionalTouchscreen = false;
        } else if ("uses-feature".equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
            if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
                mHasOptionalTouchscreen = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mManifestElement != null && !mHasOptionalTouchscreen) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, mManifestElement, xmlContext.getLocation(mManifestElement),
                    "The manifest does not declare `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`. " +
                    "Apps require a touchscreen by default, which makes them unavailable on Android TV devices.");
        }
        mManifestElement = null;
    }
}