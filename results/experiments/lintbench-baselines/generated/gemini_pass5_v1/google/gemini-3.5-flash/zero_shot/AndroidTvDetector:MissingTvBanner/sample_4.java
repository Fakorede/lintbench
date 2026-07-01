package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization " +
            "if it includes a Leanback launcher intent filter. The banner is the app " +
            "launch point that appears on the home screen in the apps and games rows.",
            Category.COMPLIANCE,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        boolean hasBannerOnApplication = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER);

        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                if (SdkConstants.TAG_ACTIVITY.equals(tagName) || SdkConstants.TAG_ACTIVITY_ALIAS.equals(tagName)) {
                    if (isLeanbackLauncher(childElement)) {
                        boolean hasBannerOnActivity = childElement.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER);
                        if (!hasBannerOnApplication && !hasBannerOnActivity) {
                            context.report(
                                    ISSUE,
                                    childElement,
                                    context.getNameLocation(childElement),
                                    "Expect `android:banner` to be defined in the `<application>` or launcher `<activity>` tag for TV compatibility"
                            );
                        }
                    }
                }
            }
        }
    }

    private boolean isLeanbackLauncher(Element activityElement) {
        NodeList childNodes = activityElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_CATEGORY.equals(filterChild.getNodeName())) {
                        Element category = (Element) filterChild;
                        String categoryName = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
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