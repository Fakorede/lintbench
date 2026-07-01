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
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {
    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n" +
            "To do this, add\n" +
            "```xml\n" +
            "`<intent-filter>`\n" +
            "    `<action  android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\"  />`\n" +
            "`</intent-filter>`\n" +
            "```\n" +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList intentFilters = element.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        if (intentFilters.getLength() == 0) {
            return;
        }

        boolean hasMediaSearchFilter = false;
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                    hasMediaSearchFilter = true;
                    break;
                }
            }
            if (hasMediaSearchFilter) {
                break;
            }
        }

        if (!hasMediaSearchFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent-filter");
        }
    }
}