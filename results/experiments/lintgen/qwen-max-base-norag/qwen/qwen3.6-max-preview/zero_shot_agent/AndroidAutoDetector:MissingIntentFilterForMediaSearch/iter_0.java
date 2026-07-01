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

public class AndroidAutoDetector extends ResourceXmlDetector {
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
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean hasIntentFilter = false;
        boolean hasMediaSearchAction = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    hasIntentFilter = true;
                    NodeList actions = childElement.getElementsByTagName(TAG_ACTION);
                    for (int j = 0; j < actions.getLength(); j++) {
                        Element action = (Element) actions.item(j);
                        String name = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                            hasMediaSearchAction = true;
                            break;
                        }
                    }
                }
            }
            if (hasMediaSearchAction) {
                break;
            }
        }

        if (hasIntentFilter && !hasMediaSearchAction) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing intent-filter for action `android.media.action.MEDIA_PLAY_FROM_SEARCH`");
        }
    }
}