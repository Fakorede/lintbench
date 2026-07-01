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
        // Look through all application children for activities and services
        NodeList appChildren = manifestElement.getChildNodes();
        for (int i = 0; i < appChildren.getLength(); i++) {
            Node appChild = appChildren.item(i);
            if (appChild.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element appElement = (Element) appChild;
            if (!"application".equals(appElement.getTagName())) {
                continue;
            }

            NodeList components = appElement.getChildNodes();
            for (int j = 0; j < components.getLength(); j++) {
                Node componentNode = components.item(j);
                if (componentNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element component = (Element) componentNode;
                String tag = component.getTagName();
                if (!TAG_SERVICE.equals(tag) && !TAG_ACTIVITY.equals(tag)) {
                    continue;
                }

                // Check if this component has an intent-filter with MEDIA_PLAY_FROM_SEARCH
                if (hasMediaPlayFromSearchIntentFilter(component)) {
                    // Already has the intent filter, no issue
                    continue;
                }

                // Check if this component is relevant to Android Auto
                // (i.e., it has an intent-filter that indicates it's an Auto component)
                if (isAutoRelevantComponent(component)) {
                    context.report(
                            MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                            component,
                            context.getLocation(component),
                            "Missing `intent-filter` for action " +
                            "`android.media.action.MEDIA_PLAY_FROM_SEARCH`"
                    );
                }
            }
        }
    }

    private boolean hasMediaPlayFromSearchIntentFilter(Element component) {
        NodeList children = component.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_INTENT_FILTER.equals(element.getTagName())) {
                continue;
            }
            // Check actions within this intent-filter
            NodeList actions = element.getChildNodes();
            for (int j = 0; j < actions.getLength(); j++) {
                Node actionNode = actions.item(j);
                if (actionNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element action = (Element) actionNode;
                if (TAG_ACTION.equals(action.getTagName())) {
                    String name = action.getAttribute(ATTR_NAME);
                    if (ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isAutoRelevantComponent(Element component) {
        // A component is Auto-relevant if it has an intent-filter with
        // android.media.browse.MediaBrowserService or other Auto-related actions
        NodeList children = component.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_INTENT_FILTER.equals(element.getTagName())) {
                continue;
            }
            NodeList filterChildren = element.getChildNodes();
            for (int j = 0; j < filterChildren.getLength(); j++) {
                Node filterChild = filterChildren.item(j);
                if (filterChild.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element filterElement = (Element) filterChild;
                if (TAG_ACTION.equals(filterElement.getTagName())) {
                    String name = filterElement.getAttribute(ATTR_NAME);
                    if (ANDROID_MEDIA_BROWSE_SERVICE.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}