package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    private static final String ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    public static final Issue ISSUE = Issue.create(
        "MissingIntentFilterForMediaSearch",
        "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
        "To support voice searches on Android Auto, you should also register an " +
        "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n" +
        "To do this, add\n" +
        "```xml\n" +
        "<intent-filter>\n" +
        "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n" +
        "</intent-filter>\n" +
        "```\n" +
        "to your `<activity>` or `<service>`.",
        Category.CORRECTNESS, 5, Severity.WARNING,
        new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean hasMediaButton = false;
        boolean hasPlayFromSearch = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                SdkConstants.TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (ACTION_MEDIA_BUTTON.equals(name)) {
                        hasMediaButton = true;
                    } else if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                        hasPlayFromSearch = true;
                    }
                }
            }
        }

        // MediaBrowserService implementations also require the voice search intent filter
        if (SdkConstants.TAG_SERVICE.equals(element.getTagName())) {
            String serviceName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (serviceName != null && (serviceName.endsWith("MediaBrowserService") ||
                serviceName.endsWith("MediaBrowserServiceCompat"))) {
                hasMediaButton = true;
            }
        }

        if (hasMediaButton && !hasPlayFromSearch) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing intent filter for media search: Android Auto requires an intent filter for " +
                "`android.media.action.MEDIA_PLAY_FROM_SEARCH` to support voice search.");
        }
    }
}