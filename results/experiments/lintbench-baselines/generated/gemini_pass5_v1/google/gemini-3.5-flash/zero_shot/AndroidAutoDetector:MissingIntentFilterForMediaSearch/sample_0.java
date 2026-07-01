package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH` " +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        List<Element> mediaBrowserServices = new ArrayList<>();
        boolean hasPlayFromSearch = false;

        NodeList services = element.getElementsByTagName("service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (hasIntentFilterAction(service, "android.media.browse.MediaBrowserService")) {
                mediaBrowserServices.add(service);
            }
            if (hasIntentFilterAction(service, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                hasPlayFromSearch = true;
            }
        }

        NodeList activities = element.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (hasIntentFilterAction(activity, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                hasPlayFromSearch = true;
            }
        }

        if (!mediaBrowserServices.isEmpty() && !hasPlayFromSearch) {
            for (Element service : mediaBrowserServices) {
                context.report(
                        ISSUE,
                        service,
                        context.getNameLocation(service),
                        "Missing `intent-filter` for `android.media.action.MEDIA_PLAY_FROM_SEARCH` action."
                );
            }
        }
    }

    private boolean hasIntentFilterAction(Element element, String actionName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String name = action.getAttributeNS(ANDROID_URI, "name");
                    if (actionName.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}