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
import java.util.Collection;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

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

    private Element mApplicationElement = null;
    private boolean mApplicationHasBanner = false;
    private final List<Element> mLeanbackActivities = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "activity-alias");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mApplicationElement = null;
        mApplicationHasBanner = false;
        mLeanbackActivities.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (!mLeanbackActivities.isEmpty() && !mApplicationHasBanner) {
                for (Element activity : mLeanbackActivities) {
                    if (!activity.hasAttributeNS("http://schemas.android.com/apk/res/android", "banner")) {
                        xmlContext.report(
                                ISSUE,
                                activity,
                                xmlContext.getNameLocation(activity),
                                "Expects a `android:banner` attribute");
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("application".equals(tagName)) {
            mApplicationElement = element;
            if (element.hasAttributeNS("http://schemas.android.com/apk/res/android", "banner")) {
                mApplicationHasBanner = true;
            }
        } else if ("activity".equals(tagName) || "activity-alias".equals(tagName)) {
            if (hasLeanbackLauncher(element)) {
                mLeanbackActivities.add(element);
            }
        }
    }

    private boolean hasLeanbackLauncher(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                boolean hasMain = false;
                boolean hasLeanback = false;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE) {
                        Element filterChildElement = (Element) filterChild;
                        String tagName = filterChildElement.getTagName();
                        if ("action".equals(tagName)) {
                            String name = filterChildElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                            if ("android.intent.action.MAIN".equals(name)) {
                                hasMain = true;
                            }
                        } else if ("category".equals(tagName)) {
                            String name = filterChildElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                                hasLeanback = true;
                            }
                        }
                    }
                }
                if (hasMain && hasLeanback) {
                    return true;
                }
            }
        }
        return false;
    }
}