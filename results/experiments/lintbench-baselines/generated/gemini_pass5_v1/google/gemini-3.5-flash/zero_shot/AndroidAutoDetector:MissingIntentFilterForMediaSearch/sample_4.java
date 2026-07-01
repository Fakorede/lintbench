package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing `MEDIA_PLAY_FROM_SEARCH` intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. " +
            "To do this, add `<intent-filter><action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /></intent-filter>` " +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element application) {
        Document document = context.document;
        if (document == null) {
            return;
        }

        boolean hasMediaBrowserService = false;
        boolean hasMediaPlayFromSearch = false;
        Element mediaBrowserServiceElement = null;

        NodeList services = document.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (hasIntentFilterAction(service, "android.media.browse.MediaBrowserService")) {
                hasMediaBrowserService = true;
                mediaBrowserServiceElement = service;
            }
            if (hasIntentFilterAction(service, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                hasMediaPlayFromSearch = true;
            }
        }

        NodeList activities = document.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (hasIntentFilterAction(activity, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                hasMediaPlayFromSearch = true;
            }
        }

        if (hasMediaBrowserService && !hasMediaPlayFromSearch) {
            context.report(
                    ISSUE,
                    mediaBrowserServiceElement,
                    context.getNameLocation(mediaBrowserServiceElement),
                    "Missing `intent-filter` for Action `android.media.action.MEDIA_PLAY_FROM_SEARCH` to support Android Auto voice searches"
            );
        }
    }

    private boolean hasIntentFilterAction(Element parent, String actionName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                for (Node grandChild = intentFilter.getFirstChild(); grandChild != null; grandChild = grandChild.getNextSibling()) {
                    if (grandChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(grandChild.getNodeName())) {
                        Element action = (Element) grandChild;
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