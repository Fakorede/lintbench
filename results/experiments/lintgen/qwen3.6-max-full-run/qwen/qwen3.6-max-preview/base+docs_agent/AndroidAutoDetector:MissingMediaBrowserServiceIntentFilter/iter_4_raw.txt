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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "MissingMediaBrowserServiceIntentFilter",
        "Missing MediaBrowserService intent-filter",
        "An Automotive Media App requires an exported service that extends `android.service.media.MediaBrowserService` " +
        "with an `intent-filter` for the action `android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
        "To do this, add\n" +
        "```xml\n" +
        "<intent-filter>\n" +
        "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
        "</intent-filter>\n" +
        "```\n" +
        "to the service that extends `android.service.media.MediaBrowserService`",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String serviceName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        boolean isMediaBrowserService = serviceName.equals(MEDIA_BROWSER_SERVICE_CLASS) ||
                serviceName.endsWith(".MediaBrowserService") ||
                serviceName.equals("MediaBrowserService");

        if (!isMediaBrowserService) {
            return;
        }

        boolean hasRequiredFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Node actionNode = actions.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE) {
                        String actionName = ((Element) actionNode).getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                            hasRequiredFilter = true;
                            break;
                        }
                    }
                }
            }
            if (hasRequiredFilter) break;
        }

        if (!hasRequiredFilter) {
            context.report(ISSUE, context.getLocation(element),
                "MediaBrowserService must have an intent-filter with action " + MEDIA_BROWSER_SERVICE_ACTION);
        }
    }
}