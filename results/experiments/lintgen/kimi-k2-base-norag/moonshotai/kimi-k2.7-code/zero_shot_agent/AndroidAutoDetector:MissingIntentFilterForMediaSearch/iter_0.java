package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";

    private static final String TAG_ACTION = "action";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_SERVICE = "service";

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an "
                    + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`."
                    + "\n\nTo do this, add\n```xml\n<intent-filter>\n"
                    + "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n"
                    + "</intent-filter>\n```\nto your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE),
            "https://developer.android.com/training/auto/audio/index.html#support_voice"
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        boolean hasPlayFromSearch = false;
        List<Element> mediaBrowserServices = new ArrayList<>();

        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tag = childElement.getTagName();
                boolean isService = TAG_SERVICE.equals(tag);
                boolean isActivity = TAG_ACTIVITY.equals(tag);

                if (isService || isActivity) {
                    boolean isMediaBrowserService = false;

                    Node filterNode = childElement.getFirstChild();
                    while (filterNode != null) {
                        if (filterNode.getNodeType() == Node.ELEMENT_NODE
                                && TAG_INTENT_FILTER.equals(filterNode.getNodeName())) {
                            Element filter = (Element) filterNode;

                            Node actionNode = filter.getFirstChild();
                            while (actionNode != null) {
                                if (actionNode.getNodeType() == Node.ELEMENT_NODE
                                        && TAG_ACTION.equals(actionNode.getNodeName())) {
                                    Element action = (Element) actionNode;
                                    String actionName = getActionName(action);

                                    if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                                        hasPlayFromSearch = true;
                                    }

                                    if (isService
                                            && ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                                        isMediaBrowserService = true;
                                    }
                                }
                                actionNode = actionNode.getNextSibling();
                            }
                        }
                        filterNode = filterNode.getNextSibling();
                    }

                    if (isService && isMediaBrowserService) {
                        mediaBrowserServices.add(childElement);
                    }
                }
            }
            child = child.getNextSibling();
        }

        if (!hasPlayFromSearch && !mediaBrowserServices.isEmpty()) {
            String message = ISSUE.getBriefDescription(TextFormat.TEXT);
            for (Element service : mediaBrowserServices) {
                context.report(ISSUE, service, context.getLocation(service), message);
            }
        }
    }

    private static String getActionName(Element action) {
        String name = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            name = action.getAttribute(ATTR_NAME);
        }
        return name;
    }
}