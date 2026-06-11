package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.EnumSet;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "TouchscreenNotOptional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must explicitly declare that a touchscreen is not required.",
            "If your app does not require a touchscreen and should be available on Android TV devices, you need to add `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>` in the manifest file.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, EnumSet.of(Scope.RESOURCE_FILE))
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST.equals(folderType);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = getAttributeValue(context, element, "android:name");
        if ("android.hardware.touchscreen".equals(name)) {
            String requiredValue = getAttributeValue(context, element, "android:required");

            // Check if the touchscreen is marked as required
            if (Boolean.parseBoolean(requiredValue)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Mark `android.hardware.touchscreen` as not required for Android TV compatibility");
            }
        }
    }

    private String getAttributeValue(XmlContext context, Element element, String attributeName) {
        Attr attribute = element.getAttributeNode(attributeName);
        return attribute != null ? attribute.getValue() : "";
    }
}