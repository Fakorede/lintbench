package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. " +
            "If you want your app to be available on TV, you must also explicitly declare " +
            "that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        boolean hasOptionalTouchscreen = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("uses-feature".equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, "name");
                    String required = childElement.getAttributeNS(ANDROID_URI, "required");
                    if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
                        hasOptionalTouchscreen = true;
                        break;
                    }
                }
            }
        }

        if (!hasOptionalTouchscreen) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Touchscreen hardware feature is implied as required. Add " +
                    "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>` " +
                    "to support Android TV."
            );
        }
    }
}