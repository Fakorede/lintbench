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
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by " +
            "Android TV.\n\n" +
            "To fix this, add\n" +
            "```xml\n" +
            "`<uses-feature android:name=\"android.software.leanback\"\n" +
            "               android:required=\"false\" />`\n" +
            "```\n" +
            "to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#leanback-req");

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS =
            "http://schemas.android.com/apk/res/android";

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Check if this manifest targets Android TV by looking for LEANBACK_LAUNCHER category
        if (!hasLeanbackLauncher(root)) {
            return;
        }

        // Check if leanback uses-feature is declared
        if (!hasLeanbackFeature(root)) {
            context.report(
                    MISSING_LEANBACK_SUPPORT,
                    root,
                    context.getLocation(root),
                    "Manifest should declare a `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` entry"
            );
        }
    }

    private boolean hasLeanbackFeature(Element root) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (TAG_USES_FEATURE.equals(element.getTagName())) {
                    String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (LEANBACK_FEATURE.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackLauncher(Element root) {
        // Look through application > activity > intent-filter > category
        NodeList rootChildren = root.getChildNodes();
        for (int i = 0; i < rootChildren.getLength(); i++) {
            org.w3c.dom.Node node = rootChildren.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (TAG_APPLICATION.equals(element.getTagName())) {
                    if (applicationHasLeanbackLauncher(element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean applicationHasLeanbackLauncher(Element application) {
        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (TAG_ACTIVITY.equals(element.getTagName())) {
                    if (activityHasLeanbackLauncher(element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean activityHasLeanbackLauncher(Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (TAG_INTENT_FILTER.equals(element.getTagName())) {
                    if (intentFilterHasLeanbackLauncher(element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackLauncher(Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (TAG_CATEGORY.equals(element.getTagName())) {
                    String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (LEANBACK_LAUNCHER.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}