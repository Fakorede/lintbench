package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {
    private static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";

    public static final Issue ISSUE = Issue.create(
            "TouchscreenNotOptional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must explicitly declare that a touchscreen is not required.",
            "If your application does not require a touchscreen and should be available on devices such as Android TV, you need to explicitly set `android:required=\"false\"` for the `android.hardware.touchscreen` feature in your manifest file.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Nullable
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of("uses-feature");
    }

    @NonNull
    @Override
    public List<Issue> checkTag(@NonNull XmlContext context) throws Exception {
        Element element = (Element) context.getXml().getDocument();
        String name = element.getAttribute("name");

        if (FEATURE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttribute("required");
            if ("true".equals(required) || required.isEmpty()) {
                context.report(ISSUE, context.getLocation(element), "Set `android:required=\"false\"` for touchscreen feature.");
            }
        }

        return ImmutableList.of();
    }
}