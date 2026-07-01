package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. " +
            "If you want your app to be available on TV, you must also explicitly declare " +
            "that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        Element touchscreenFeature = null;
        boolean hasOptionalTouchscreen = false;

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if ("uses-feature".equals(child.getTagName())) {
                    String name = child.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                    if ("android.hardware.touchscreen".equals(name)) {
                        touchscreenFeature = child;
                        String required = child.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                        if ("false".equals(required)) {
                            hasOptionalTouchscreen = true;
                            break;
                        }
                    }
                }
            }
        }

        if (hasOptionalTouchscreen) {
            return;
        }

        if (touchscreenFeature != null) {
            context.report(ISSUE, touchscreenFeature, context.getLocation(touchscreenFeature),
                    "Touchscreen hardware feature must be explicitly marked as not required for TV compatibility");
        } else {
            context.report(ISSUE, element, context.getLocation(element),
                    "Add `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>` to support Android TV");
        }
    }
}