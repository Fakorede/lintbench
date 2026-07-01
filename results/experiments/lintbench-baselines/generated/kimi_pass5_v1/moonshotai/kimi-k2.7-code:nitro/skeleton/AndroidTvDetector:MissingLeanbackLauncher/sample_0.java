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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String FEATURE_TELEVISION = "android.hardware.type.television";
    private static final String FEATURE_LEANBACK = "android.software.leanback";

    private boolean mIsTvApp;
    private boolean mHasLeanbackLauncher;

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "When an application declares support for TV devices, it must include an activity "
                            + "(or activity alias) in its manifest with an intent filter containing "
                            + "the `android.intent.category.LEANBACK_LAUNCHER` category. This allows "
                            + "the application to be launched from the Android TV home screen. See "
                            + "https://developer.android.com/training/tv/start/start.html#tv-activity.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsTvApp || mHasLeanbackLauncher) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        Element application = getApplicationElement(xmlContext);
        if (application != null) {
            xmlContext.report(
                    ISSUE,
                    application,
                    xmlContext.getLocation(application),
                    "Expected an activity with `android.intent.category.LEANBACK_LAUNCHER` in its intent filter");
        } else {
            xmlContext.report(
                    ISSUE,
                    xmlContext.document.getDocumentElement(),
                    xmlContext.getLocation(xmlContext.document.getDocumentElement()),
                    "Expected an activity with `android.intent.category.LEANBACK_LAUNCHER` in its intent filter");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (FEATURE_TELEVISION.equals(name) || FEATURE_LEANBACK.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (required.isEmpty() || Boolean.parseBoolean(required)) {
                    mIsTvApp = true;
                }
            }
        } else if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncherCategory(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private static boolean hasLeanbackLauncherCategory(Element activity) {
        NodeList intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList categories = intentFilter.getElementsByTagName(TAG_CATEGORY);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Element getApplicationElement(XmlContext context) {
        NodeList applications = context.document.getElementsByTagName("application");
        if (applications.getLength() > 0) {
            return (Element) applications.item(0);
        }
        return null;
    }
}