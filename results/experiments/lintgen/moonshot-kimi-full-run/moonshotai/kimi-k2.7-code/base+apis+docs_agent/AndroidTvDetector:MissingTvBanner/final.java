package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ATTR_BANNER = "banner";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue MISSING_BANNER = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. The banner is the app launch point that appears on the home screen in the apps and games rows.",
            Category.create("TV", 100),
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        String banner = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_BANNER);
        if (banner != null && !banner.isEmpty()) {
            return;
        }

        if (hasLeanbackLauncher(element)) {
            context.report(
                    MISSING_BANNER,
                    element,
                    context.getLocation(element),
                    "The application is missing an Android TV banner (`android:banner`) but declares a Leanback launcher intent filter.");
        }
    }

    private static boolean hasLeanbackLauncher(Element application) {
        if (hasCategory(application, SdkConstants.TAG_ACTIVITY, CATEGORY_LEANBACK_LAUNCHER)) {
            return true;
        }
        return hasCategory(application, SdkConstants.TAG_ACTIVITY_ALIAS, CATEGORY_LEANBACK_LAUNCHER);
    }

    private static boolean hasCategory(Element application, String activityTag, String categoryName) {
        NodeList activities = application.getElementsByTagName(activityTag);
        for (int i = 0, n = activities.getLength(); i < n; i++) {
            Element activity = (Element) activities.item(i);
            NodeList filters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
            for (int j = 0, m = filters.getLength(); j < m; j++) {
                Element filter = (Element) filters.item(j);
                NodeList categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
                for (int k = 0, p = categories.getLength(); k < p; k++) {
                    Element category = (Element) categories.item(k);
                    String name = category.getAttributeNS(
                            SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (categoryName.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}