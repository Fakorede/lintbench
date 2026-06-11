package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ConvertToWebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1, it supports transparency and lossless conversion as well.",
            "Previously, launcher icons were required to be in the PNG format but that restriction is no longer there, so lint now flags these.",
            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Collections.emptySet())
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNode("name");
        if (nameAttr != null && nameAttr.getValue().endsWith(".png")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Consider converting this PNG icon to WebP for better performance.");
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:icon");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.endsWith(".png")) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Consider converting this PNG icon to WebP for better performance.");
        }
    }

}