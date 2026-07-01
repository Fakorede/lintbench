package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "intent-filter for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n" +
            "To do this, add\n" +
            "`<intent-filter>`\n" +
            "    `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />`\n" +
            "`</intent-filter>`\n" +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "androidx.media.MediaBrowserServiceCompat";
    private static final String MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean hasMediaBrowserService = false;
        boolean hasMediaPlayFromSearch = false;

        NodeList filters = element.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Node filterNode = filters.item(i);
            if (filterNode.getNodeType() == Node.ELEMENT_NODE) {
                Element filter = (Element) filterNode;
                NodeList actions = filter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Node actionNode = actions.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element action = (Element) actionNode;
                        String name = action.getAttributeNS(ANDROID_URI, "name");
                        if (MEDIA_BROWSER_SERVICE.equals(name) || MEDIA_BROWSER_SERVICE_COMPAT.equals(name)) {
                            hasMediaBrowserService = true;
                        }
                        if (MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                            hasMediaPlayFromSearch = true;
                        }
                    }
                }
            }
        }

        if (hasMediaBrowserService && !hasMediaPlayFromSearch) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing intent-filter for `android.media.action.MEDIA_PLAY_FROM_SEARCH`");
        }
    }
}