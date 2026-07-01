package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String TOUCHSCREEN_HARDWARE = "android.hardware.touchscreen";

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows:\n\n"
                    + "<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_MANIFEST.equals(element.getTagName())) {
            return;
        }

        NodeList children = element.getChildNodes();
        boolean hasOptionalTouchscreen = false;

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_USES_FEATURE.equals(child.getNodeName())) {
                Element usesFeature = (Element) child;
                String name = usesFeature.getAttribute(ATTR_NAME);
                if (TOUCHSCREEN_HARDWARE.equals(name)) {
                    String required = usesFeature.getAttribute(ATTR_REQUIRED);
                    if ("false".equals(required)) {
                        hasOptionalTouchscreen = true;
                    }
                }
            }
        }

        if (!hasOptionalTouchscreen) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Touchscreen hardware is implicitly required; if this app is intended for TV, add `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`");
        }
    }
}