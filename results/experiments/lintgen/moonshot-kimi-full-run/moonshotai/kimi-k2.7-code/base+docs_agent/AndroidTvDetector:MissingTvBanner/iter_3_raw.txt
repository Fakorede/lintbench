package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;

import com.android.tools.lint.detector.api.Category;
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
    private static final String LEANBACK_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue MISSING_TV_BANNER = Issue.create(
            "MissingTvBanner",
            "Missing home screen banner for TV",
            "A TV application must provide a home screen banner for each localization if it "
                    + "includes a Leanback launcher intent filter. The banner is the app launch "
                    + "point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE),
            "https://developer.android.com/training/tv/start/start.html#banner"
    );

    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getNodeName();
        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        } else if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private static boolean hasLeanbackLauncher(Element element) {
        NodeList filters = element.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0, n = filters.getLength(); i < n; i++) {
            Element filter = (Element) filters.item(i);
            NodeList categories = filter.getElementsByTagName(TAG_CATEGORY);
            for (int j = 0, m = categories.getLength(); j < m; j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (LEANBACK_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mHasLeanbackLauncher && mApplicationElement != null
                && !mApplicationElement.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
            context.report(
                    MISSING_TV_BANNER,
                    mApplicationElement,
                    context.getLocation(mApplicationElement),
                    "Missing home screen banner for TV; add the `android:banner` attribute "
                            + "to the application element"
            );
        }
    }
}