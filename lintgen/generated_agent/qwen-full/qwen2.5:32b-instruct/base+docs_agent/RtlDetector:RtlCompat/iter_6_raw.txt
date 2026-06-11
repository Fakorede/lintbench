package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.utils.Pair;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

import com.android.tools.lint.detector.api.*;

public class RtlDetector extends Detector implements XmlScanner {
    private static final String TEXT_ALIGNMENT = "textAlignment";
    private static final String GRAVITY = "gravity";
    private static final String LAYOUT_GRAVITY = "layout_gravity";

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    private static final Issue ISSUE = Issue.create(
            "RtlTextAlignment",
            "textAlignment is used but gravity or layout_gravity is missing for API level below 17",
            "If you are supporting older versions than API 17, you must also specify a gravity or layout_gravity attribute.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    RtlDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr textAlignmentAttr = getAttribute(element, TEXT_ALIGNMENT);
        if (textAlignmentAttr != null) {
            Attr gravityAttr = getAttribute(element, GRAVITY);
            Attr layoutGravityAttr = getAttribute(element, LAYOUT_GRAVITY);

            if (gravityAttr == null && layoutGravityAttr == null) {
                int apiLevel = context.getDriver().getProject().getHighestSdkVersion();
                if (apiLevel < 17) {
                    String message = "textAlignment is used but gravity or layout_gravity is missing for API level below 17";
                    Location location = context.getLocation(textAlignmentAttr);
                    context.report(ISSUE, element, location, message);
                }
            }
        }
    }

    private Attr getAttribute(Element element, String attributeName) {
        return (Attr) element.getAttributeNode(attributeName);
    }
}