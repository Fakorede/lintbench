package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_EXPORTED = "android:exported";

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends " +
            "`android.service.media.MediaBrowserService` with an `intent-filter` for the action " +
            "`android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
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

    public AndroidAutoDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element applicationElement) {
        // Check if this is an automotive app by looking for automotive app descriptor
        if (!isAutomotiveApp(context, applicationElement)) {
            return;
        }

        // Find all service elements that extend MediaBrowserService
        List<Element> mediaBrowserServices = findMediaBrowserServices(applicationElement);

        if (mediaBrowserServices.isEmpty()) {
            return;
        }

        // Check each MediaBrowserService for proper intent-filter
        for (Element serviceElement : mediaBrowserServices) {
            if (!hasMediaBrowserServiceIntentFilter(serviceElement)) {
                context.report(
                        MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER,
                        serviceElement,
                        context.getNameLocation(serviceElement),
                        "This service does not have an `<intent-filter>` for action " +
                        "`android.media.browse.MediaBrowserService`"
                );
            }
        }
    }

    private boolean isAutomotiveApp(XmlContext context, Element applicationElement) {
        // Look for meta-data with automotive app descriptor
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if ("meta-data".equals(element.getTagName())) {
                    String name = element.getAttribute(ATTR_NAME);
                    if ("com.google.android.gms.car.application".equals(name) ||
                            "com.google.android.gms.car.application.theme".equals(name)) {
                        return true;
                    }
                    // Check for automotive_app_desc resource reference
                    String resource = element.getAttribute("android:resource");
                    if (resource != null && resource.contains("automotive_app_desc")) {
                        return true;
                    }
                }
            }
        }

        // Also check manifest-level meta-data and uses-feature
        Document document = applicationElement.getOwnerDocument();
        if (document != null) {
            Element manifestElement = document.getDocumentElement();
            if (manifestElement != null) {
                NodeList manifestChildren = manifestElement.getChildNodes();
                for (int i = 0; i < manifestChildren.getLength(); i++) {
                    Node child = manifestChildren.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element element = (Element) child;
                        if ("uses-feature".equals(element.getTagName())) {
                            String name = element.getAttribute(ATTR_NAME);
                            if ("android.hardware.type.automotive".equals(name)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        // Check if there are any MediaBrowserService subclasses - if so, treat as potential
        // automotive app candidate (the check will still only fire if there's a MediaBrowserService)
        // For broader detection, we check if the app has any MediaBrowserService
        return hasAnyMediaBrowserService(applicationElement);
    }

    private boolean hasAnyMediaBrowserService(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_SERVICE.equals(element.getTagName())) {
                    String name = element.getAttribute(ATTR_NAME);
                    if (name != null && !name.isEmpty()) {
                        // We can't resolve class hierarchy here easily, but we look for
                        // services that have the media browser action in intent-filters
                        // or check if name contains known patterns
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<Element> findMediaBrowserServices(Element applicationElement) {
        List<Element> result = new ArrayList<>();
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_SERVICE.equals(element.getTagName())) {
                    // Check if this service has a MediaBrowserService intent-filter action
                    // OR if we should flag services that are missing the intent-filter
                    // We look for services that already have the MediaBrowserService action
                    // to identify them, or we rely on class name hints
                    if (isMediaBrowserService(element)) {
                        result.add(element);
                    }
                }
            }
        }
        return result;
    }

    private boolean isMediaBrowserService(Element serviceElement) {
        // Check if the service element has android:name that suggests MediaBrowserService
        // We look for the MediaBrowserService action in intent-filters to identify candidates
        // Also check for explicit class references
        String serviceName = serviceElement.getAttribute(ATTR_NAME);

        // Check child intent-filters for the MediaBrowserService action
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_INTENT_FILTER.equals(element.getTagName())) {
                    if (intentFilterHasMediaBrowserServiceAction(element)) {
                        // Already has the right intent filter, not a problem
                        return false;
                    }
                }
            }
        }

        // If we get here, the service doesn't have the MediaBrowserService intent-filter
        // We need to determine if it's supposed to be a MediaBrowserService
        // Since we can't resolve class hierarchy in XML lint, we check for
        // services that are exported (or could be) and have media-related names
        // This is a heuristic approach

        // Check meta-data that might indicate it's a MediaBrowserService
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if ("meta-data".equals(element.getTagName())) {
                    // Some services declare meta-data for MediaBrowserService
                    String metaName = element.getAttribute(ATTR_NAME);
                    if (metaName != null && metaName.contains("media")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_INTENT_FILTER.equals(element.getTagName())) {
                    if (intentFilterHasMediaBrowserServiceAction(element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasMediaBrowserServiceAction(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_ACTION.equals(element.getTagName())) {
                    String actionName = element.getAttribute(ATTR_NAME);
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}