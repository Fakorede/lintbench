package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n" +
            "To do this, add\n" +
            "`<intent-filter>`\n" +
            "    `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />`\n" +
            "`</intent-filter>`\n" +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON";
    private static final String ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Set<String> actions = new HashSet<>();
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = child.getNodeName();
                if (TAG_INTENT_FILTER.equals(tagName)) {
                    Element intentFilter = (Element) child;
                    NodeList filterChildren = intentFilter.getChildNodes();
                    for (int j = 0, m = filterChildren.getLength(); j < m; j++) {
                        Node filterChild = filterChildren.item(j);
                        if (filterChild.getNodeType() == Node.ELEMENT_NODE && TAG_ACTION.equals(filterChild.getNodeName())) {
                            String name = ((Element) filterChild).getAttributeNS(ANDROID_URI, ATTR_NAME);
                            if (name != null && !name.isEmpty()) {
                                actions.add(name);
                            }
                        }
                    }
                }
            }
        }

        boolean hasRelevantAction = actions.contains(ACTION_MEDIA_BUTTON) || actions.contains(ACTION_MEDIA_BROWSER_SERVICE);
        boolean hasPlayFromSearch = actions.contains(ACTION_MEDIA_PLAY_FROM_SEARCH);

        if (hasRelevantAction && !hasPlayFromSearch) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `MEDIA_PLAY_FROM_SEARCH` intent-filter for Android Auto voice search support");
        }
    }
}