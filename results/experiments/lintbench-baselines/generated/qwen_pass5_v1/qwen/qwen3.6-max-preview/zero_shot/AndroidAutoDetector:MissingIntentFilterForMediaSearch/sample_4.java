package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST)
    );

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (hasMediaPlayFromSearchIntentFilter(element)) {
            return;
        }

        // Only flag components that are already exposed via an intent-filter
        // to avoid noise on internal/non-exported components.
        if (hasIntentFilter(element)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Add an `<intent-filter>` for action `android.media.action.MEDIA_PLAY_FROM_SEARCH` " +
                    "to support voice searches on Android Auto");
        }
    }

    private static boolean hasIntentFilter(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    TAG_INTENT_FILTER.equals(child.getNodeName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMediaPlayFromSearchIntentFilter(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    TAG_INTENT_FILTER.equals(child.getNodeName())) {
                if (intentFilterContainsAction((Element) child, ACTION_MEDIA_PLAY_FROM_SEARCH)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean intentFilterContainsAction(@NonNull Element intentFilter, @NonNull String actionName) {
        NodeList actions = intentFilter.getElementsByTagName(TAG_ACTION);
        for (int i = 0, n = actions.getLength(); i < n; i++) {
            Element action = (Element) actions.item(i);
            if (actionName.equals(action.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME))) {
                return true;
            }
        }
        return false;
    }
}