package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.CATEGORY_LEANBACK_LAUNCHER;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT_FILTER;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "Missing TV Banner",
                    "A TV application must provide a home screen banner for each localization if"
                            + " it includes a Leanback launcher intent filter. The banner is the"
                            + " app launch point that appears on the home screen in the apps and"
                            + " games rows. Add `android:banner` to the `<application>` element.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackLauncher;
    private boolean mHasBanner;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_ACTIVITY, NODE_ACTIVITY_ALIAS);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mHasLeanbackLauncher = false;
        mHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NODE_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            if (element.hasAttributeNS(ANDROID_URI, ATTR_BANNER)
                    && !element.getAttributeNS(ANDROID_URI, ATTR_BANNER).isEmpty()) {
                mHasBanner = true;
            }
        } else if (NODE_ACTIVITY.equals(tag) || NODE_ACTIVITY_ALIAS.equals(tag)) {
            NodeList intentFilters = element.getElementsByTagName(NODE_INTENT_FILTER);
            for (int i = 0; i < intentFilters.getLength(); i++) {
                Element intentFilter = (Element) intentFilters.item(i);
                NodeList categories = intentFilter.getElementsByTagName(NODE_CATEGORY);
                for (int j = 0; j < categories.getLength(); j++) {
                    Element category = (Element) categories.item(j);
                    String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        mHasLeanbackLauncher = true;
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mHasLeanbackLauncher && !mHasBanner && mApplicationElement != null) {
            context.report(
                    ISSUE,
                    mApplicationElement,
                    context.getLocation(mApplicationElement),
                    "Missing TV banner: a TV app with a Leanback launcher intent filter must"
                            + " declare `android:banner` on the `<application>` element.");
        }
    }
}