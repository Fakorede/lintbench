package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

import java.util.EnumSet;

public class IconDetector extends Detector implements Detector.XmlScanner {
    public static final Issue ISSUE = Issue.create(
            "ConvertToWebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1, it supports transparency and lossless conversion as well.",
            "Previously, launcher icons were required to be in the PNG format but that restriction is no longer there, so lint now flags these.",
            Category.PERFORMANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE))
    );

    @Override
    public List<Location> checkXmlFile(@NonNull XmlContext context) {
        Element element = context.getEvent().getElement();
        if (element != null && "item".equals(element.getNodeName())) {
            String format = element.getAttribute("format");
            if ("png".equalsIgnoreCase(format)) {
                return Collections.singletonList(context.getLocation(element));
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Issue getIssue() {
        return ISSUE;
    }
}