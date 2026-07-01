package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import java.util.EnumSet;

public class AndroidTvDetector extends ResourceXmlDetector {
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
        new Implementation(AndroidTvDetector.class, EnumSet.of(Scope.MANIFEST))
    );

    private boolean hasOptionalTouchscreen = false;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public void beforeCheckFile(Context context) {
        hasOptionalTouchscreen = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (SdkConstants.TAG_USES_FEATURE.equals(element.getTagName())) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.hardware.touchscreen".equals(name)) {
                String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if ("false".equals(required)) {
                    hasOptionalTouchscreen = true;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!hasOptionalTouchscreen) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            if (root != null) {
                xmlContext.report(ISSUE, xmlContext.getLocation(root),
                    "To support Android TV, explicitly declare `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`");
            }
        }
    }
}