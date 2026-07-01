package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "ImpliedTouchscreenHardware",
        "Touchscreen not optional",
        "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows:\n" +
        "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
        Category.USABILITY,
        5,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST)
    );

    private boolean hasOptionalTouchscreen;

    @Override
    public void beforeCheckFile(Context context) {
        hasOptionalTouchscreen = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.hardware.touchscreen".equals(name)) {
            String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            if (SdkConstants.VALUE_FALSE.equals(required)) {
                hasOptionalTouchscreen = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!hasOptionalTouchscreen) {
            String message = "Touchscreen hardware feature is required by default. Declare " +
                    "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>` " +
                    "to support Android TV.";
            context.report(ISSUE, Location.create(context.file), message);
        }
    }
}