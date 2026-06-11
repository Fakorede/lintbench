package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneApp",
            "Invalid attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, android:required=\"false\" is disallowed. A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:required");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        if ("uses-feature".equals(element.getAttribute("name")) &&
                "android.hardware.type.watch".equals(element.getAttribute("android:name"))) {
            String requiredValue = attribute.getValue();
            if (requiredValue != null && !Boolean.parseBoolean(requiredValue)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "For the `android.hardware.type.watch` uses-feature, android:required=\"false\" is disallowed.");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }
}