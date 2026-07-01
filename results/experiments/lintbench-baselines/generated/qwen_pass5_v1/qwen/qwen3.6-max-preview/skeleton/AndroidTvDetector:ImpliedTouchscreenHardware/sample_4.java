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
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows: `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private boolean mTouchscreenOptional;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mTouchscreenOptional = false;
        mManifestElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mTouchscreenOptional && mManifestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mManifestElement,
                    xmlContext.getLocation(mManifestElement),
                    "Touchscreen hardware feature is required by default. Add `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>` to support TV devices.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("manifest".equals(tag)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            String required = element.getAttributeNS(ANDROID_URI, "required");
            if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
                mTouchscreenOptional = true;
            }
        }
    }
}