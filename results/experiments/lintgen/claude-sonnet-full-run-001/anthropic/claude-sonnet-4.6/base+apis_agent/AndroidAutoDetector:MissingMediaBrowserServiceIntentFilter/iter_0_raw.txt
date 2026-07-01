package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.android.xml.AndroidManifest;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Lint detector that checks for missing MediaBrowserService intent-filter
 * in Android Auto media apps.
 */
public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an " +
            "`intent-filter` for the action `android.media.browse.MediaBrowserService` " +
            "to be able to browse and play media.\n\n" +
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

    private static final String ANDROID_SERVICE_MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_ANDROID_NAME = "android:name";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_META_DATA = "meta-data";
    private static final String AUTOMOTIVE_APP_DESC = "com.google.android.gms.car.application";
    private static final String AUTOMOTIVE_APP_DESC_MEDIA = "automotive_app_desc";

    public AndroidAutoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(AndroidManifest.NODE_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element applicationElement) {
        // Check if this is an automotive media app by looking for the automotive meta-data
        if (!isAutomotiveMediaApp(applicationElement)) {
            return;
        }

        // Find services that extend MediaBrowserService
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_SERVICE.equals(element.getTagName())) {
                continue;
            }

            // Check if this service has the MediaBrowserService intent-filter
            if (hasMediaBrowserServiceIntentFilter(element)) {
                // Found a service with the correct intent-filter, no issue
                return;
            }
        }

        // No service with MediaBrowserService intent-filter found
        // Report on the application element
        context.report(
                MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                applicationElement,
                context.getLocation(applicationElement),
                "Missing `android.media.browse.MediaBrowserService` intent-filter for " +
                "a service that extends `android.service.media.MediaBrowserService`"
        );
    }

    /**
     * Checks if the application element contains automotive meta-data indicating
     * this is an automotive media app.
     */
    private boolean isAutomotiveMediaApp(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_META_DATA.equals(element.getTagName())) {
                continue;
            }
            String name = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_NAME);
            if (name == null || name.isEmpty()) {
                name = element.getAttribute(ATTR_ANDROID_NAME);
            }
            if (AUTOMOTIVE_APP_DESC.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given service element has an intent-filter with the
     * MediaBrowserService action.
     */
    private boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_INTENT_FILTER.equals(element.getTagName())) {
                continue;
            }
            // Check if this intent-filter has the MediaBrowserService action
            if (hasAction(element, MEDIA_BROWSER_SERVICE_ACTION)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the given intent-filter element has an action with the specified name.
     */
    private boolean hasAction(Element intentFilterElement, String actionName) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            if (!TAG_ACTION.equals(element.getTagName())) {
                continue;
            }
            String name = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_NAME);
            if (name == null || name.isEmpty()) {
                name = element.getAttribute(ATTR_ANDROID_NAME);
            }
            if (actionName.equals(name)) {
                return true;
            }
        }
        return false;
    }
}