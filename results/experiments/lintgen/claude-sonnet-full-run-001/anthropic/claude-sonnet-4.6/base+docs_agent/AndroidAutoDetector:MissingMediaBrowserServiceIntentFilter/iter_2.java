package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Lint detector that checks for missing MediaBrowserService intent-filter in Android Auto apps.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an " +
            "`intent-filter` for the action " +
            "`android.media.browse.MediaBrowserService` to be able to browse " +
            "and play media.\n\n" +
            "To do this, add\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
            "</intent-filter>\n" +
            "```\n" +
            "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#config_manifest");

    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public AndroidAutoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this service has the MediaBrowserService action in its intent-filter
        boolean hasMediaBrowserServiceAction = hasMediaBrowserServiceAction(element);

        if (!hasMediaBrowserServiceAction) {
            context.report(
                    MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                    element,
                    context.getLocation(element),
                    "This service does not have an `intent-filter` for action " +
                    "`android.media.browse.MediaBrowserService`"
            );
        }
    }

    private boolean hasMediaBrowserServiceAction(Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String childTag = childElement.getLocalName();
            if (childTag == null) {
                childTag = childElement.getTagName();
            }

            if (TAG_INTENT_FILTER.equals(childTag)) {
                NodeList intentFilterChildren = childElement.getChildNodes();
                for (int j = 0; j < intentFilterChildren.getLength(); j++) {
                    Node intentFilterChild = intentFilterChildren.item(j);
                    if (intentFilterChild.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element actionElement = (Element) intentFilterChild;
                    String actionTag = actionElement.getLocalName();
                    if (actionTag == null) {
                        actionTag = actionElement.getTagName();
                    }

                    if (TAG_ACTION.equals(actionTag)) {
                        String actionName = actionElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}