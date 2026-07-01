package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "if it includes a Leanback launcher intent filter. The banner is the "
                            + "app launch point that appears on the home screen in the apps and "
                            + "games rows.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_NAME = "name";
    private static final String VALUE_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasLeanbackLauncher;
    private boolean mHasBanner;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mHasLeanbackLauncher = false;
        mHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("application".equals(tagName)) {
            mApplicationElement = element;
            if (element.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
                mHasBanner = true;
            }
        } else if ("activity".equals(tagName)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
                mHasBanner = true;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (VALUE_LEANBACK_LAUNCHER.equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mHasLeanbackLauncher && !mHasBanner) {
            Element target = mApplicationElement;
            if (target == null && context.getDocument() != null) {
                target = context.getDocument().getDocumentElement();
            }
            if (target != null) {
                context.report(
                        ISSUE,
                        target,
                        context.getLocation(target),
                        "Expects `android:banner` attribute to be defined when Leanback launcher is present");
            }
        }
    }
}