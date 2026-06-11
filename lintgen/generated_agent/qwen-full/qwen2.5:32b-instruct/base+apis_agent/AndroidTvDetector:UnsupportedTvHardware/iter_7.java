package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;
import java.util.EnumSet;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue UNSUPPORTED_TV_HARDWARE_FEATURE = Issue.create(
            "UnsupportedTVHardwareFeature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature.",
            "Any `uses-feature` not explicitly marked with `required=\"false\"` is necessary on the device to be installed on. Ensure that any features that might prevent it from being installed on a TV device are reviewed and marked as not required in the manifest.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, EnumSet.of(Scope.XML_FILE))
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr != null && isUnsupportedFeature(nameAttr.getValue())) {
            Attr requiredAttr = element.getAttributeNode("required");
            if (requiredAttr == null || !"false".equals(requiredAttr.getValue())) {
                context.report(UNSUPPORTED_TV_HARDWARE_FEATURE, element,
                        context.getLocation(element),
                        "This feature should be marked as not required for TV devices.");
            }
        }
    }

    private boolean isUnsupportedFeature(String name) {
        List<String> unsupportedFeatures = Collections.unmodifiableList(List.of(
                "android.hardware.touchscreen",
                "android.hardware.faketouch"
        ));
        return unsupportedFeatures.contains(name);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }
}