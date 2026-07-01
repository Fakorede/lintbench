package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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

import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should register an intent-filter for the action android.media.action.MEDIA_PLAY_FROM_SEARCH. Add an `<intent-filter>` with an `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList children = element.getChildNodes();
        boolean hasMediaPlayFromSearch = false;

        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String tagName = ((Element) child).getTagName();
            if (SdkConstants.TAG_ACTIVITY.equals(tagName)
                    || SdkConstants.TAG_SERVICE.equals(tagName)) {
                if (hasMediaPlayFromSearchIntentFilter((Element) child)) {
                    hasMediaPlayFromSearch = true;
                    break;
                }
            }
        }

        if (!hasMediaPlayFromSearch) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `MEDIA_PLAY_FROM_SEARCH` intent-filter required for Android Auto voice search");
        }
    }

    private static boolean hasMediaPlayFromSearchIntentFilter(Element component) {
        NodeList intentFilters = component.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0, n = intentFilters.getLength(); i < n; i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList actions = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION);
            for (int j = 0, m = actions.getLength(); j < m; j++) {
                Element action = (Element) actions.item(j);
                String name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}