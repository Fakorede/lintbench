package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE_MISSING_MEDIA_SEARCH_INTENT_FILTER = Issue.create(
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
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_MEDIA_BROWSE_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element manifestElement) {
        // Find application element
        NodeList manifestChildren = manifestElement.getChildNodes();
        for (int i = 0; i < manifestChildren.getLength(); i++) {
            Node manifestChild = manifestChildren.item(i);
            if (manifestChild.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element applicationElement = (Element) manifestChild;
            if (!"application".equals(applicationElement.getTagName())) {
                continue;
            }

            // Look through activities and services
            NodeList appChildren = applicationElement.getChildNodes();
            for (int j = 0; j < appChildren.getLength(); j++) {
                Node appChild = appChildren.item(j);
                if (appChild.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element component = (Element) appChild;
                String tagName = component.getTagName();
                if (!TAG_SERVICE.equals(tagName) && !TAG_ACTIVITY.equals(tagName)) {
                    continue;
                }

                // Check if this component has an intent-filter for MediaBrowserService
                // or any intent-filter that might indicate it's an Auto-related component
                if (isAutoMediaComponent(component)) {
                    // Check if it also has MEDIA_PLAY_FROM_SEARCH intent-filter
                    if (!hasMediaPlayFromSearchIntentFilter(component)) {
                        context.report(
                                ISSUE_MISSING_MEDIA_SEARCH_INTENT_FILTER,
                                component,
                                context.getLocation(component),
                                "To support voice searches on Android Auto, you should also " +
                                "register an `intent-filter` for the action " +
                                "`android.media.action.MEDIA_PLAY_FROM_SEARCH`"
                        );
                    }
                }
            }
        }
    }

    private boolean isAutoMediaComponent(Element component) {
        // Check if the component has an intent-filter with MediaBrowserService action
        // or is declared as a MediaBrowserService
        String componentName = component.getAttribute(ATTR_NAME);
        if (componentName != null && componentName.contains("MediaBrowser")) {
            return true;
        }

        NodeList children = component.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (intentFilterHasAction(childElement, ANDROID_MEDIA_BROWSE_SERVICE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMediaPlayFromSearchIntentFilter(Element component) {
        NodeList children = component.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (intentFilterHasAction(childElement, ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasAction(Element intentFilter, String actionName) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_ACTION.equals(childElement.getTagName())) {
                String name = childElement.getAttribute(ATTR_NAME);
                if (actionName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}