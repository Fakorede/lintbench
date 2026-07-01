package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "if it includes a Leanback launcher intent filter. The banner is the app "
                            + "launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasAppBanner;
    private List<Element> mLeanbackActivities;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasAppBanner = false;
        mLeanbackActivities = new ArrayList<Element>();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("application".equals(tagName)) {
            mHasAppBanner = element.hasAttributeNS("http://schemas.android.com/apk/res/android", "banner");
        } else if ("activity".equals(tagName)) {
            if (isLeanbackActivity(element)) {
                mLeanbackActivities.add(element);
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (!mHasAppBanner) {
            for (Element activity : mLeanbackActivities) {
                if (!activity.hasAttributeNS("http://schemas.android.com/apk/res/android", "banner")) {
                    context.report(
                            ISSUE,
                            activity,
                            context.getNameLocation(activity),
                            "An activity with the Leanback launcher intent-filter must define a banner "
                                    + "via the `android:banner` attribute, or the `<application>` element must "
                                    + "define an `android:banner` attribute.");
                }
            }
        }
    }

    private boolean isLeanbackActivity(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList grandChildren = intentFilter.getChildNodes();
                for (int j = 0; j < grandChildren.getLength(); j++) {
                    Node grandChild = grandChildren.item(j);
                    if (grandChild.getNodeType() == Node.ELEMENT_NODE && "category".equals(grandChild.getNodeName())) {
                        Element category = (Element) grandChild;
                        String categoryName = category.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                        if ("android.intent.category.LEANBACK_LAUNCHER".equals(categoryName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}