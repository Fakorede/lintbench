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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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

    private boolean mHasLeanbackLauncher;
    private boolean mHasAppBanner;
    private Element mLeanbackActivity;
    private boolean mLeanbackActivityHasBanner;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncher = false;
        mHasAppBanner = false;
        mLeanbackActivity = null;
        mLeanbackActivityHasBanner = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackLauncher && !mHasAppBanner && mLeanbackActivity != null && !mLeanbackActivityHasBanner) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, mLeanbackActivity, xmlContext.getLocation(mLeanbackActivity),
                    "TV applications must provide a home screen banner for each localization if they include a Leanback launcher intent filter.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        String banner = element.getAttributeNS(ANDROID_URI, "banner");
        boolean hasBanner = banner != null && !banner.isEmpty();

        if ("application".equals(tag)) {
            if (hasBanner) {
                mHasAppBanner = true;
            }
        } else if ("activity".equals(tag)) {
            if (hasLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
                mLeanbackActivity = element;
                mLeanbackActivityHasBanner = hasBanner;
            }
        }
    }

    private boolean hasLeanbackLauncher(@NonNull Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                NodeList filters = child.getChildNodes();
                for (int j = 0; j < filters.getLength(); j++) {
                    Node filter = filters.item(j);
                    if (filter.getNodeType() == Node.ELEMENT_NODE && "category".equals(filter.getNodeName())) {
                        String name = ((Element) filter).getAttributeNS(ANDROID_URI, "name");
                        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}