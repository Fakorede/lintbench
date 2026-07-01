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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String TV_HARDWARE_FEATURE = "android.hardware.type.television";
    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mIsTvApp;
    private boolean mHasLeanbackLauncher;

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using an `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        if (mIsTvApp && !mHasLeanbackLauncher) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            if (root != null) {
                context.report(
                        ISSUE,
                        context.getLocation(root),
                        "The manifest must declare an activity with an `android.intent.category.LEANBACK_LAUNCHER` intent filter for Android TV apps.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name) || TV_HARDWARE_FEATURE.equals(name)) {
                mIsTvApp = true;
            }
        } else if (TAG_CATEGORY.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }
}