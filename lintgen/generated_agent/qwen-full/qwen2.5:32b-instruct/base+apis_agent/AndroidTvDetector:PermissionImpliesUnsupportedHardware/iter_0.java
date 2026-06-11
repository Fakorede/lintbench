package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedHardwarePermission",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature.",
            "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, true));

    private static final List<String> HARDWARE_PERMISSIONS = Collections.singletonList("android.permission.CAMERA");

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-permission");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNode("android:name");
        if (nameAttr != null && HARDWARE_PERMISSIONS.contains(nameAttr.getValue())) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This permission implies a hardware feature that may not be supported on TV devices. Consider declaring the corresponding `uses-feature` element with `required=\"false\"`.");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST.equals(folderType);
    }
}