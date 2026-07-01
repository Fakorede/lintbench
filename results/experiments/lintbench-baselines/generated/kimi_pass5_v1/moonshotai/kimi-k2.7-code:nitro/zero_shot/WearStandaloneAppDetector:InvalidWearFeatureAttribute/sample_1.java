package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final String HARDWARE_TYPE_WATCH = "android.hardware.type.watch";
    private static final String MESSAGE =
            "A single APK for Wear and non-Wear devices is not supported; "
                    + "do not set `android:required=\"false\"` for "
                    + "`<uses-feature android:name=\"android.hardware.type.watch\" />`.";

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "A single APK cannot support both Wear and non-Wear devices, so "
                    + "`android:required=\"false\"` is not allowed for the "
                    + "`android.hardware.type.watch` uses-feature.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("android:name");
        if (HARDWARE_TYPE_WATCH.equals(name)) {
            String required = element.getAttribute("android:required");
            if ("false".equalsIgnoreCase(required)) {
                context.report(
                        ISSUE,
                        element,
                        context.getValueLocation(element, "android:required"),
                        MESSAGE);
            }
        }
    }
}