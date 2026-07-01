package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collections;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingIntentFilterForMediaSearch",
        "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
        "To support voice searches on Android Auto, you should also register an " +
        "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH` " +
        "in your `<activity>` or `<service>`.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(
            AndroidAutoDetector.class,
            Scope.MANIFEST_SCOPE
        )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Element application = null;
        NodeList applicationList = root.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        if (applicationList.getLength() > 0) {
            application = (Element) applicationList.item(0);
        }

        if (application == null) {
            return;
        }

        boolean hasMediaBrowserService = false;
        boolean hasMediaPlayFromSearch = false;
        Element mediaBrowserServiceElement = null;

        NodeList services = application.getElementsByTagName(SdkConstants.TAG_SERVICE);
        for (int i = 0; i < services.getLength(); i++) {
            Node node = services.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element service = (Element) node;
                if (hasIntentAction(service, "android.media.browse.MediaBrowserService")) {
                    hasMediaBrowserService = true;
                    mediaBrowserServiceElement = service;
                }
                if (hasIntentAction(service, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                    hasMediaPlayFromSearch = true;
                }
            }
        }

        NodeList activities = application.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Node node = activities.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element activity = (Element) node;
                if (hasIntentAction(activity, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                    hasMediaPlayFromSearch = true;
                }
            }
        }

        if (hasMediaBrowserService && !hasMediaPlayFromSearch) {
            Location location = context.getNameLocation(mediaBrowserServiceElement != null ? mediaBrowserServiceElement : application);
            context.report(
                ISSUE,
                location,
                "Missing `MEDIA_PLAY_FROM_SEARCH` intent-filter to support voice searches on Android Auto"
            );
        }
    }

    private boolean hasIntentAction(Element element, String actionName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                        Element action = (Element) filterChild;
                        String name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if (actionName.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}