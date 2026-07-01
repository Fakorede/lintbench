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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each "
                            + "localization if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home "
                            + "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Element mApplicationElement;
    private boolean mApplicationHasBanner;
    private final List<Element> mLeanbackActivities = new ArrayList<>();
    private final Set<Element> mActivitiesWithBanner = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "activity-alias");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mApplicationElement = null;
        mApplicationHasBanner = false;
        mLeanbackActivities.clear();
        mActivitiesWithBanner.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mLeanbackActivities.isEmpty() && !mApplicationHasBanner) {
            boolean allHaveBanner = true;
            for (Element activity : mLeanbackActivities) {
                if (!mActivitiesWithBanner.contains(activity)) {
                    allHaveBanner = false;
                    break;
                }
            }
            if (!allHaveBanner && context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                Element target = mApplicationElement != null ? mApplicationElement : mLeanbackActivities.get(0);
                xmlContext.report(
                        ISSUE,
                        target,
                        xmlContext.getLocation(target),
                        "Expects to provide a TV banner");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("application".equals(tagName)) {
            mApplicationElement = element;
            if (element.hasAttributeNS(ANDROID_URI, "banner")) {
                mApplicationHasBanner = true;
            }
        } else if ("activity".equals(tagName) || "activity-alias".equals(tagName)) {
            if (element.hasAttributeNS(ANDROID_URI, "banner")) {
                mActivitiesWithBanner.add(element);
            }
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    Element childElement = (Element) child;
                    if ("intent-filter".equals(childElement.getTagName())) {
                        NodeList filterChildren = childElement.getChildNodes();
                        for (int j = 0; j < filterChildren.getLength(); j++) {
                            Node filterChild = filterChildren.item(j);
                            if (filterChild instanceof Element) {
                                Element filterChildElement = (Element) filterChild;
                                if ("category".equals(filterChildElement.getTagName())) {
                                    String categoryName = filterChildElement.getAttributeNS(ANDROID_URI, "name");
                                    if ("android.intent.category.LEANBACK_LAUNCHER".equals(categoryName)) {
                                        mLeanbackActivities.add(element);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}