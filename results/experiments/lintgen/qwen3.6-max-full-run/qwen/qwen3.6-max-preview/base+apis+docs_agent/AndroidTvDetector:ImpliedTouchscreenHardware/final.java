package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. " +
            "If you want your app to be available on TV, you must also explicitly declare " +
            "that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        boolean hasOptionalTouchscreen = false;

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if ("uses-feature".equals(child.getTagName())) {
                    String name = child.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                    String required = child.getAttributeNS(SdkConstants.ANDROID_URI, "required");
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
                    "This app does not declare `android.hardware.touchscreen` as not required. " +
                    "Add `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>` " +
                    "to make the app available on Android TV."
            );
        }
    }
}