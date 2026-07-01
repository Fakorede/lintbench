package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register " +
            "an `intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH` " +
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
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList services = element.getElementsByTagName("service");
        NodeList activities = element.getElementsByTagName("activity");

        boolean isMediaApp = false;
        Element mediaServiceElement = null;

        // Check if this is a media app by looking for media browser/session services
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (hasIntentFilterAction(service, "android.media.browse.MediaBrowserService")
                    || hasIntentFilterAction(service, "androidx.media3.session.MediaSessionService")
                    || hasIntentFilterAction(service, "androidx.media3.session.MediaLibraryService")) {
                isMediaApp = true;
                mediaServiceElement = service;
                break;
            }
        }

        if (!isMediaApp) {
            return;
        }

        boolean hasMediaPlayFromSearch = false;

        // Check if any service has the search action
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (hasIntentFilterAction(service, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                hasMediaPlayFromSearch = true;
                break;
            }
        }

        // Check if any activity has the search action
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
            Element targetElement = mediaServiceElement != null ? mediaServiceElement : element;
            context.report(
                    ISSUE,
                    targetElement,
                    context.getLocation(targetElement),
                    "Missing `intent-filter` for Action `MEDIA_PLAY_FROM_SEARCH` to support voice searches on Android Auto."
            );
        }
    }

    private boolean hasIntentFilterAction(Element element, String actionName) {
        NodeList intentFilters = element.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (actionName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}