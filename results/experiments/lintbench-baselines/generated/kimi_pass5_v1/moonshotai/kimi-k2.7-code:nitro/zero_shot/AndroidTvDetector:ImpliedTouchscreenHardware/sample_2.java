package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TOUCHSCREEN_HARDWARE = "android.hardware.touchscreen";

    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows:\n"
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`\n\n"
                            + "For more information, see https://developer.android.com/guide/topics/manifest/uses-feature-element.html",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        NodeList features = document.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0, n = features.getLength(); i < n; i++) {
            Element element = (Element) features.item(i);
            if (TOUCHSCREEN_HARDWARE.equals(element.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if ("false".equals(required)) {
                    return;
                }
            }
        }

        Element manifest = document.getDocumentElement();
        if (manifest != null) {
            context.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    manifest,
                    context.getNameLocation(manifest),
                    "Touchscreen not optional: declare `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`");
        }
    }
}