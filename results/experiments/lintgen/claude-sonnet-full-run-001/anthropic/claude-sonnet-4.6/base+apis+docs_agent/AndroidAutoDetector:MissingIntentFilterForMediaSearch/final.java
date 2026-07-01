package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

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

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE, TAG_ACTIVITY);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Walk the entire document to find MediaBrowserService and check for MEDIA_PLAY_FROM_SEARCH
        boolean hasMediaBrowserService = false;
        boolean hasMediaPlayFromSearch = false;
        Element mediaBrowserServiceElement = null;

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Look through application children for service/activity elements
        NodeList appChildren = root.getChildNodes();
        for (int i = 0; i < appChildren.getLength(); i++) {
            Node appChild = appChildren.item(i);
            if (appChild.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element appElement = (Element) appChild;
            if ("application".equals(appElement.getTagName())) {
                NodeList componentChildren = appElement.getChildNodes();
                for (int j = 0; j < componentChildren.getLength(); j++) {
                    Node componentChild = componentChildren.item(j);
                    if (componentChild.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element component = (Element) componentChild;
                    String tag = component.getTagName();
                    if (TAG_SERVICE.equals(tag) || TAG_ACTIVITY.equals(tag)) {
                        if (hasIntentFilterWithAction(component, ACTION_MEDIA_BROWSER_SERVICE)) {
                            hasMediaBrowserService = true;
                            if (mediaBrowserServiceElement == null) {
                                mediaBrowserServiceElement = component;
                            }
                        }
                        if (hasIntentFilterWithAction(component, ACTION_MEDIA_PLAY_FROM_SEARCH)) {
                            hasMediaPlayFromSearch = true;
                        }
                    }
                }
            }
        }

        if (hasMediaBrowserService && !hasMediaPlayFromSearch && mediaBrowserServiceElement != null) {
            context.report(
                    MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                    mediaBrowserServiceElement,
                    context.getLocation(mediaBrowserServiceElement),
                    "Missing `intent-filter` for action " +
                    "`android.media.action.MEDIA_PLAY_FROM_SEARCH`"
            );
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Handled in visitDocument
    }

    private boolean hasIntentFilterWithAction(Element element, String actionName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (hasAction(childElement, actionName)) {
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
                String name = getAndroidName(childElement);
                if (actionName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getAndroidName(Element element) {
        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        return name != null ? name : "";
    }
}