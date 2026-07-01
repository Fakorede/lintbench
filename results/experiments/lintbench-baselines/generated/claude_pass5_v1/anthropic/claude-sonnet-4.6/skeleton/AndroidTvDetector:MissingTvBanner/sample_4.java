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
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTION = "action";

    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_NAME = "name";

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home "
                            + "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Whether the current file has a Leanback launcher intent filter */
    private boolean mHasLeanbackLauncherIntent;

    /** Whether the current file has a banner attribute on the application or activity */
    private boolean mHasBanner;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncherIntent = false;
        mHasBanner = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackLauncherIntent && !mHasBanner) {
            context.report(
                    ISSUE,
                    context.getLocation(context.document.getDocumentElement()),
                    "A TV application must provide a home screen banner (`android:banner`) "
                            + "for each localization if it includes a Leanback launcher intent filter.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        // Check for banner attribute
        String banner = element.getAttributeNS(ANDROID_NS, ATTR_BANNER);
        if (banner != null && !banner.isEmpty()) {
            mHasBanner = true;
        }

        // Check for Leanback launcher intent filter in activity or application
        if (TAG_ACTIVITY.equals(tagName) || TAG_APPLICATION.equals(tagName)) {
            if (hasLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncherIntent = true;
            }
        }
    }

    /**
     * Checks whether the given element has a child intent-filter that contains
     * a category element with name android.intent.category.LEANBACK_LAUNCHER.
     */
    private boolean hasLeanbackLauncherIntentFilter(@NonNull Element element) {
        NodeList intentFilters = element.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList categories = intentFilter.getElementsByTagName(TAG_CATEGORY);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}