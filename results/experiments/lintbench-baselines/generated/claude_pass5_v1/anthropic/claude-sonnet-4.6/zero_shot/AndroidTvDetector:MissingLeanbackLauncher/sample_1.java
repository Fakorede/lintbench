package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue MISSING_LEANBACK_LAUNCHER = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity " +
            "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` " +
            "intent filter.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#tv-activity");

    private static final String ANDROID_MANIFEST_TAG = "manifest";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTION = "action";
    private static final String TAG_USES_FEATURE = "uses-feature";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String HARDWARE_FEATURE_TV = "android.hardware.type.television";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ANDROID_MANIFEST_TAG);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Check if this app targets TV by looking for uses-feature for TV/leanback
        boolean targetsTv = false;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_USES_FEATURE.equals(element.getTagName())) {
                    String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (HARDWARE_FEATURE_TV.equals(featureName) ||
                            LEANBACK_FEATURE.equals(featureName)) {
                        targetsTv = true;
                        break;
                    }
                }
            }
        }

        if (!targetsTv) {
            return;
        }

        // Find application element
        Element applicationElement = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    TAG_APPLICATION.equals(((Element) child).getTagName())) {
                applicationElement = (Element) child;
                break;
            }
        }

        if (applicationElement == null) {
            return;
        }

        // Check if any activity has a LEANBACK_LAUNCHER intent filter
        boolean hasLeanbackLauncher = false;
        NodeList activityNodes = applicationElement.getChildNodes();
        for (int i = 0; i < activityNodes.getLength(); i++) {
            Node activityNode = activityNodes.item(i);
            if (activityNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element activityElement = (Element) activityNode;
            if (!TAG_ACTIVITY.equals(activityElement.getTagName())) {
                continue;
            }

            // Check intent filters in this activity
            NodeList intentFilterNodes = activityElement.getChildNodes();
            for (int j = 0; j < intentFilterNodes.getLength(); j++) {
                Node intentFilterNode = intentFilterNodes.item(j);
                if (intentFilterNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element intentFilterElement = (Element) intentFilterNode;
                if (!TAG_INTENT_FILTER.equals(intentFilterElement.getTagName())) {
                    continue;
                }

                boolean hasActionMain = false;
                boolean hasLeanbackCategory = false;

                NodeList filterChildren = intentFilterElement.getChildNodes();
                for (int k = 0; k < filterChildren.getLength(); k++) {
                    Node filterChild = filterChildren.item(k);
                    if (filterChild.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element filterChildElement = (Element) filterChild;
                    String tagName = filterChildElement.getTagName();
                    String nameAttr = filterChildElement.getAttributeNS(ANDROID_URI, ATTR_NAME);

                    if (TAG_ACTION.equals(tagName) && ACTION_MAIN.equals(nameAttr)) {
                        hasActionMain = true;
                    } else if (TAG_CATEGORY.equals(tagName) &&
                            LEANBACK_LAUNCHER_CATEGORY.equals(nameAttr)) {
                        hasLeanbackCategory = true;
                    }
                }

                if (hasActionMain && hasLeanbackCategory) {
                    hasLeanbackLauncher = true;
                    break;
                }
            }

            if (hasLeanbackLauncher) {
                break;
            }
        }

        if (!hasLeanbackLauncher) {
            context.report(
                    MISSING_LEANBACK_LAUNCHER,
                    applicationElement,
                    context.getLocation(applicationElement),
                    "Expecting an activity to have `android.intent.category.LEANBACK_LAUNCHER` " +
                    "intent filter."
            );
        }
    }
}