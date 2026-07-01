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

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_ANDROID_NAME = "android:name";

    public AndroidAutoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only check service elements
        if (!TAG_SERVICE.equals(element.getTagName()) &&
            !TAG_SERVICE.equals(element.getLocalName())) {
            return;
        }

        // Check if this service extends MediaBrowserService
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_NAME);
        if (name == null || name.isEmpty()) {
            name = element.getAttribute(ATTR_ANDROID_NAME);
        }

        if (name == null || name.isEmpty()) {
            return;
        }

        // We need to check if this service has the MediaBrowserService action in its intent-filter
        // We look for intent-filter children with the appropriate action
        boolean hasMediaBrowserServiceAction = false;

        NodeList children = element.getChildNodes();
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
                // Check if this intent-filter has the MediaBrowserService action
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
                        String actionName = actionElement.getAttributeNS(
                                "http://schemas.android.com/apk/res/android", ATTR_NAME);
                        if (actionName == null || actionName.isEmpty()) {
                            actionName = actionElement.getAttribute(ATTR_ANDROID_NAME);
                        }
                        if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                            hasMediaBrowserServiceAction = true;
                            break;
                        }
                    }
                }
            }

            if (hasMediaBrowserServiceAction) {
                break;
            }
        }

        // If this service doesn't have the MediaBrowserService action, report it
        // But we only report if the service is related to MediaBrowserService
        // We check the name to see if it might be a MediaBrowserService subclass
        // Since we can't resolve class hierarchy in manifest-only analysis,
        // we look for services that have intent-filters but missing the specific action,
        // or we check if the app declares automotive use-feature
        if (!hasMediaBrowserServiceAction) {
            // Check if the manifest declares automotive feature or if we should check all services
            // For this detector, we check if the service name contains or is related to
            // MediaBrowserService - but since we can't do class resolution here,
            // we need to check if the parent manifest has automotive feature declared
            Element manifestElement = (Element) element.getParentNode().getParentNode();
            if (manifestElement == null) {
                return;
            }

            // Check if this is an automotive app by looking for the automotive use-feature
            boolean isAutomotiveApp = isAutomotiveApp(manifestElement);

            if (isAutomotiveApp) {
                // Report the issue on this service element
                context.report(
                        MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
                        element,
                        context.getLocation(element),
                        "This service does not have an `intent-filter` for action " +
                        "`android.media.browse.MediaBrowserService`"
                );
            }
        }
    }

    private boolean isAutomotiveApp(Element manifestElement) {
        // Walk through the manifest to find uses-feature for automotive
        NodeList children = manifestElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tag = childElement.getLocalName();
            if (tag == null) {
                tag = childElement.getTagName();
            }

            if ("uses-feature".equals(tag)) {
                String featureName = childElement.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", ATTR_NAME);
                if (featureName == null || featureName.isEmpty()) {
                    featureName = childElement.getAttribute(ATTR_ANDROID_NAME);
                }
                if ("android.hardware.type.automotive".equals(featureName)) {
                    return true;
                }
            }
        }
        return false;
    }
}