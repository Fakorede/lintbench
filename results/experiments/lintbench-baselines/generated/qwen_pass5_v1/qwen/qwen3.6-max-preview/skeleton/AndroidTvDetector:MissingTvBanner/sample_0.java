package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. The banner is the app launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private boolean hasLeanbackLauncher;
    private boolean hasBanner;
    private Element applicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        hasLeanbackLauncher = false;
        hasBanner = false;
        applicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (hasLeanbackLauncher && !hasBanner) {
            XmlContext xmlContext = (XmlContext) context;
            Element locationElement = applicationElement != null ? applicationElement : xmlContext.document.getDocumentElement();
            xmlContext.report(
                    ISSUE,
                    locationElement,
                    xmlContext.getLocation(locationElement),
                    "TV applications must provide a home screen banner in the `<application>` tag " +
                    "when declaring a `LEANBACK_LAUNCHER` intent filter. " +
                    "Add `android:banner=\"@drawable/...\"` to the `<application>` element."
            );
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            applicationElement = element;
            if (element.hasAttributeNS(ANDROID_URI, "banner")) {
                hasBanner = true;
            }
        } else if ("category".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
            }
        }
    }
}