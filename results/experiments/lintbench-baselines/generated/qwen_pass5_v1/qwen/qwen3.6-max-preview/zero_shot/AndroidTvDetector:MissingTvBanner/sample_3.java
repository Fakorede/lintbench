package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. " +
            "The banner is the app launch point that appears on the home screen in the apps and games rows.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_APPLICATION = "application";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_BANNER = "banner";

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "activity-alias");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (hasLeanbackLauncherIntentFilter(element)) {
            if (!hasBanner(element)) {
                Element application = getApplicationElement(element);
                if (application == null || !hasBanner(application)) {
                    context.report(ISSUE, context.getLocation(element),
                            "TV apps must provide a home screen banner for each localization. " +
                            "Add `android:banner` to the `<application>` or `<activity>` tag.");
                }
            }
        }
    }

    private boolean hasLeanbackLauncherIntentFilter(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element filter = (Element) child;
                NodeList filterChildren = filter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node fc = filterChildren.item(j);
                    if (fc.getNodeType() == Node.ELEMENT_NODE && TAG_CATEGORY.equals(fc.getNodeName())) {
                        String name = ((Element) fc).getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME);
                        if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasBanner(@NonNull Element element) {
        String banner = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_BANNER);
        return banner != null && !banner.isEmpty();
    }

    @Nullable
    private Element getApplicationElement(@NonNull Element element) {
        Document doc = element.getOwnerDocument();
        if (doc != null) {
            NodeList apps = doc.getElementsByTagName(TAG_APPLICATION);
            if (apps.getLength() > 0) {
                Node appNode = apps.item(0);
                if (appNode.getNodeType() == Node.ELEMENT_NODE) {
                    return (Element) appNode;
                }
            }
        }
        return null;
    }
}