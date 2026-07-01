package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;

public class AndroidAutoDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an intent-filter for the action " +
            "`android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n" +
            "To do this, add:\n" +
            "```xml\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n" +
            "</intent-filter>\n" +
            "```\n" +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    ).setMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (hasMediaPlayFromSearchIntentFilter(element)) {
            return;
        }
        context.report(ISSUE, element, context.getLocation(element),
                "Missing intent-filter for action `" + ACTION_MEDIA_PLAY_FROM_SEARCH + "`");
    }

    private boolean hasMediaPlayFromSearchIntentFilter(Element component) {
        NodeList intentFilters = component.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Node intentFilter = intentFilters.item(i);
            if (intentFilter.getNodeType() == Node.ELEMENT_NODE) {
                NodeList actions = ((Element) intentFilter).getElementsByTagName(TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Node action = actions.item(j);
                    if (action.getNodeType() == Node.ELEMENT_NODE) {
                        String name = ((Element) action).getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}