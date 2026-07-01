package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.List;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "MissingLeanbackLauncher",
        "Missing Leanback Launcher Intent Filter",
        "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (hasLeanbackLauncher(element)) {
            return;
        }

        Element application = getFirstElementByTagName(element, SdkConstants.TAG_APPLICATION);
        Element location = application != null ? application : element;
        context.report(ISSUE, location, context.getLocation(location),
            "Missing Leanback Launcher Intent Filter");
    }

    private static boolean hasLeanbackLauncher(Element manifest) {
        if (checkActivities(manifest.getElementsByTagName(SdkConstants.TAG_ACTIVITY))) {
            return true;
        }
        return checkActivities(manifest.getElementsByTagName("activity-alias"));
    }

    private static boolean checkActivities(NodeList activities) {
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList filters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
            for (int j = 0; j < filters.getLength(); j++) {
                Element filter = (Element) filters.item(j);
                NodeList categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
                for (int k = 0; k < categories.getLength(); k++) {
                    Element category = (Element) categories.item(k);
                    String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Element getFirstElementByTagName(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return (Element) list.item(0);
        }
        return null;
    }
}