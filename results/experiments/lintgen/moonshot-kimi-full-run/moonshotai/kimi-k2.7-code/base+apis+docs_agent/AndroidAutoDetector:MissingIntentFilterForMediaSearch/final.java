package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an intent-filter "
                    + "for the action android.media.action.MEDIA_PLAY_FROM_SEARCH. Add it to an "
                    + "<activity> or <service> in your manifest.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    private boolean mHasMediaPlayFromSearch;
    private final List<Location> mMediaBrowserComponents = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasMediaPlayFromSearch = false;
        mMediaBrowserComponents.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        boolean isMediaBrowser = false;
        boolean hasPlayFromSearch = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) childNode;
            if (!TAG_INTENT_FILTER.equals(child.getTagName())) {
                continue;
            }

            for (Element action : getActionElements(child)) {
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                    isMediaBrowser = true;
                } else if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                    hasPlayFromSearch = true;
                }
            }
        }

        if (hasPlayFromSearch) {
            mHasMediaPlayFromSearch = true;
        }
        if (isMediaBrowser) {
            mMediaBrowserComponents.add(context.getLocation(element));
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasMediaPlayFromSearch || mMediaBrowserComponents.isEmpty()) {
            return;
        }

        String message = "Missing intent-filter for action "
                + ACTION_MEDIA_PLAY_FROM_SEARCH
                + "; add it to an <activity> or <service> to support voice searches on Android Auto.";

        for (Location location : mMediaBrowserComponents) {
            context.report(ISSUE, location, message);
        }
    }

    private static List<Element> getActionElements(@NonNull Element intentFilter) {
        List<Element> actions = new ArrayList<>();
        NodeList nodes = intentFilter.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ACTION.equals(((Element) node).getTagName())) {
                actions.add((Element) node);
            }
        }
        return actions;
    }
}