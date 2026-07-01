package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. " +
            "To do this, add `<intent-filter><action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /></intent-filter>` " +
            "to your `<activity>` or `<service>`.",
            Category.PRODUCTIVITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        NodeList services = document.getElementsByTagName("service");
        NodeList activities = document.getElementsByTagName("activity");

        Element mediaBrowserServiceElement = null;
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (hasIntentFilterAction(service, "android.media.browse.MediaBrowserService")) {
                mediaBrowserServiceElement = service;
                break;
            }
        }

        if (mediaBrowserServiceElement == null) {
            return;
        }

        boolean hasMediaPlayFromSearch = false;

        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (hasIntentFilterAction(service, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                hasMediaPlayFromSearch = true;
                break;
            }
        }

        if (!hasMediaPlayFromSearch) {
            for (int i = 0; i < activities.getLength(); i++) {
                Element activity = (Element) activities.item(i);
                if (hasIntentFilterAction(activity, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                    hasMediaPlayFromSearch = true;
                    break;
                }
            }
        }

        if (!hasMediaPlayFromSearch) {
            Location location = context.getNameLocation(mediaBrowserServiceElement);
            context.report(
                    ISSUE,
                    mediaBrowserServiceElement,
                    location,
                    "Missing `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent-filter on any activity or service"
            );
        }
    }

    private boolean hasIntentFilterAction(Element element, String actionName) {
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
                        String name = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
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