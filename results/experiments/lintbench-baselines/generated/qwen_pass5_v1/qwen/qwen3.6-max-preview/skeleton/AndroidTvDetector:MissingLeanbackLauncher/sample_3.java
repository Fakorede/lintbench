package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using an android.intent.category.LEANBACK_LAUNCHER intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean hasLeanbackFeature;
    private boolean hasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        hasLeanbackFeature = false;
        hasLeanbackLauncher = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("uses-feature".equals(tag)) {
            String name = element.getAttribute("android:name");
            String required = element.getAttribute("android:required");
            boolean isRequired = !"false".equals(required);
            if (isRequired && ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name))) {
                hasLeanbackFeature = true;
            }
        } else if ("category".equals(tag)) {
            String name = element.getAttribute("android:name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (hasLeanbackFeature && !hasLeanbackLauncher) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    xmlContext.getLocation(xmlContext.document.getDocumentElement()),
                    "Missing Leanback Launcher Intent Filter: TV apps must declare an activity with android.intent.category.LEANBACK_LAUNCHER");
        }
    }
}