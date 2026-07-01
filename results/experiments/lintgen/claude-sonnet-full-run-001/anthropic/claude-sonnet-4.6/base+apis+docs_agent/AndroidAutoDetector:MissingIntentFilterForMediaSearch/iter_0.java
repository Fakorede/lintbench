package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n" +
            "To do this, add\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n" +
            "</intent-filter>\n" +
            "```\n" +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_MEDIA_BROWSE_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_ANDROID_NAME = "android:name";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ANDROID_AUTO_META_DATA = "com.google.android.gms.car.application";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element manifestElement) {
        // Check if this is an Android Auto app by looking for the meta-data tag
        // in application element
        Element applicationElement = getChildElement(manifestElement, "application");
        if (applicationElement == null) {
            return;
        }

        // Check if this app declares Android Auto support
        if (!hasAndroidAutoMetaData(applicationElement)) {
            return;
        }

        // Check if there's a MediaBrowserService with the proper intent filter
        boolean hasMediaBrowserService = false;
        boolean hasMediaPlayFromSearch = false;

        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tagName = element.getTagName();

            if (TAG_SERVICE.equals(tagName) || TAG_ACTIVITY.equals(tagName)) {
                // Check if this service/activity is a MediaBrowserService
                boolean isMediaBrowserService = isMediaBrowserService(element);
                if (isMediaBrowserService) {
                    hasMediaBrowserService = true;
                }

                // Check if this element has MEDIA_PLAY_FROM_SEARCH intent filter
                if (hasMediaPlayFromSearchIntentFilter(element)) {
                    hasMediaPlayFromSearch = true;
                }
            }
        }

        // If there's a MediaBrowserService but no MEDIA_PLAY_FROM_SEARCH intent filter, report
        if (hasMediaBrowserService && !hasMediaPlayFromSearch) {
            // Find the MediaBrowserService element to report on
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) child;
                String tagName = element.getTagName();

                if ((TAG_SERVICE.equals(tagName) || TAG_ACTIVITY.equals(tagName))
                        && isMediaBrowserService(element)) {
                    context.report(
                            MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                            element,
                            context.getLocation(element),
                            "Missing `intent-filter` for action " +
                            "`android.media.action.MEDIA_PLAY_FROM_SEARCH`"
                    );
                    break;
                }
            }
        }
    }

    private boolean hasAndroidAutoMetaData(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (TAG_META_DATA.equals(element.getTagName())) {
                String name = element.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", ATTR_NAME);
                if (name.isEmpty()) {
                    name = element.getAttribute(ATTR_ANDROID_NAME);
                }
                if (ANDROID_AUTO_META_DATA.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isMediaBrowserService(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (hasAction(childElement, "android.service.media.MediaBrowserService")) {
                    return true;
                }
            }
        }

        // Also check by class name
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_NAME);
        if (name.isEmpty()) {
            name = element.getAttribute(ATTR_ANDROID_NAME);
        }
        if (name.contains("MediaBrowserService")) {
            return true;
        }

        return false;
    }

    private boolean hasMediaPlayFromSearchIntentFilter(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (hasAction(childElement, ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAction(Element intentFilterElement, String actionName) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_ACTION.equals(childElement.getTagName())) {
                String name = childElement.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", ATTR_NAME);
                if (name.isEmpty()) {
                    name = childElement.getAttribute(ATTR_ANDROID_NAME);
                }
                if (actionName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Element getChildElement(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (tagName.equals(element.getTagName())) {
                    return element;
                }
            }
        }
        return null;
    }
}