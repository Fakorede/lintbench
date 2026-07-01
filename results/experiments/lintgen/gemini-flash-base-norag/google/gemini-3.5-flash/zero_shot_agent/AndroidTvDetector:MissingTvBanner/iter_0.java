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
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_INTENT_FILTER);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean hasLeanbackLauncher = false;
        NodeList categories = element.getElementsByTagName(SdkConstants.TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Node node = categories.item(i);
            if (node instanceof Element) {
                Element category = (Element) node;
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    hasLeanbackLauncher = true;
                    break;
                }
            }
        }

        if (!hasLeanbackLauncher) {
            return;
        }

        Node parentNode = element.getParentNode();
        if (parentNode instanceof Element) {
            Element parent = (Element) parentNode;
            String tagName = parent.getTagName();
            if (SdkConstants.TAG_ACTIVITY.equals(tagName) || SdkConstants.TAG_ACTIVITY_ALIAS.equals(tagName)) {
                if (hasBanner(parent)) {
                    return;
                }

                Node grandParentNode = parent.getParentNode();
                if (grandParentNode instanceof Element) {
                    Element grandParent = (Element) grandParentNode;
                    if (SdkConstants.TAG_APPLICATION.equals(grandParent.getTagName())) {
                        if (hasBanner(grandParent)) {
                            return;
                        }
                    }
                }
            }
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Expect `android:banner` to be defined in the manifest for the Leanback launcher activity or the application"
        );
    }

    private boolean hasBanner(Element element) {
        String banner = element.getAttributeNS(SdkConstants.ANDROID_URI, "banner");
        return banner != null && !banner.isEmpty();
    }
}