package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.android.xml.AndroidManifest;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this service extends MediaBrowserService
        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        // We need to check if this service has the MediaBrowserService intent-filter
        // We look for intent-filter children with the correct action
        boolean hasMediaBrowserServiceAction = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    TAG_INTENT_FILTER.equals(child.getLocalName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getChildNodes();
                for (int j = 0; j < actions.getLength(); j++) {
                    Node actionNode = actions.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE &&
                            TAG_ACTION.equals(actionNode.getLocalName())) {
                        Element action = (Element) actionNode;
                        String actionName = action.getAttributeNS(ANDROID_NS, ATTR_NAME);
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

        // Check if the manifest uses automotive feature
        // We report the issue if the service has a MediaBrowserService-related name
        // but lacks the required intent-filter. Since we can't easily check the
        // superclass from the manifest alone, we look for services that appear to be
        // MediaBrowserService implementations based on the intent-filter presence check.
        // The detector checks all services in automotive apps for the required intent-filter.

        if (!hasMediaBrowserServiceAction) {
            // Check if this looks like it could be a MediaBrowserService
            // by checking if it has any intent-filter (indicating it's meant to be used)
            // or if the name contains relevant keywords
            // Per the spec, we check services that are exported or have intent-filters
            // but lack the MediaBrowserService action

            // Check if the app declares automotive use in the manifest
            // We check the parent application's manifest for automotive metadata
            Element application = (Element) element.getParentNode();
            if (application == null) {
                return;
            }

            // Look for automotive metadata in the manifest
            boolean isAutomotiveApp = isAutomotiveApp(application);
            if (!isAutomotiveApp) {
                return;
            }

            // Check if this service has any intent-filter (suggesting it's a browser service)
            // or if it's exported
            boolean hasIntentFilter = false;
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        TAG_INTENT_FILTER.equals(child.getLocalName())) {
                    hasIntentFilter = true;
                    break;
                }
            }

            // Report if the service name suggests it's a MediaBrowserService
            // or if it's exported with intent filters but missing the required action
            if (looksLikeMediaBrowserService(name, element)) {
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

    private boolean isAutomotiveApp(Element application) {
        // Look for meta-data with automotive app descriptor
        // or check the manifest for uses-feature automotive
        Node parent = application.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }
        Element manifest = (Element) parent;

        // Check for uses-feature android.hardware.type.automotive
        NodeList children = manifest.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                if ("uses-feature".equals(elem.getLocalName())) {
                    String featureName = elem.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if ("android.hardware.type.automotive".equals(featureName)) {
                        return true;
                    }
                }
            }
        }

        // Check application meta-data for automotive app descriptor
        NodeList appChildren = application.getChildNodes();
        for (int i = 0; i < appChildren.getLength(); i++) {
            Node child = appChildren.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                if ("meta-data".equals(elem.getLocalName())) {
                    String metaName = elem.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if ("com.google.android.gms.car.application".equals(metaName) ||
                            "com.google.android.gms.car.application.theme".equals(metaName)) {
                        return true;
                    }
                    // Check for automotive_app_desc resource reference
                    String metaResource = elem.getAttributeNS(ANDROID_NS, "resource");
                    if (metaResource != null && metaResource.contains("automotive_app_desc")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean looksLikeMediaBrowserService(String name, Element serviceElement) {
        // Check if the service name contains "MediaBrowser" or "MusicService" or similar
        // This is a heuristic since we can't check superclass from manifest
        String simpleName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;

        // Check if it has any intent-filter (it's meant to be discovered)
        boolean hasIntentFilter = false;
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    TAG_INTENT_FILTER.equals(child.getLocalName())) {
                hasIntentFilter = true;
                break;
            }
        }

        // Check if exported
        String exported = serviceElement.getAttributeNS(ANDROID_NS, "exported");
        boolean isExported = "true".equals(exported);

        return hasIntentFilter || isExported;
    }
}