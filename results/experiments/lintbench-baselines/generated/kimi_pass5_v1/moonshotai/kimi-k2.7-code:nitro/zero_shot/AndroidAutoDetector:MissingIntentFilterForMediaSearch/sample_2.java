package com.android.tools.lint.checks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    public static final Issue MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an "
                    + "`intent-filter` for the action "
                    + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`. Add an `<intent-filter>` "
                    + "with `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` "
                    + "to an `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE),
            "https://developer.android.com/training/auto/audio/index.html#support_voice"
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (!ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null || !TAG_MANIFEST.equals(root.getTagName())) {
            return;
        }

        Element application = getFirstChildElementByTagName(root, TAG_APPLICATION);
        if (application == null) {
            return;
        }

        boolean hasPlayFromSearch = false;
        List<Element> mediaBrowserServices = new ArrayList<>();

        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element component = (Element) child;
            String tag = component.getTagName();
            if (!TAG_ACTIVITY.equals(tag) && !TAG_SERVICE.equals(tag)) {
                continue;
            }

            boolean isMediaBrowserService = false;
            NodeList filters = component.getElementsByTagName(TAG_INTENT_FILTER);
            for (int j = 0; j < filters.getLength(); j++) {
                Element filter = (Element) filters.item(j);
                NodeList actions = filter.getElementsByTagName(TAG_ACTION);
                for (int k = 0; k < actions.getLength(); k++) {
                    Element action = (Element) actions.item(k);
                    String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                        hasPlayFromSearch = true;
                    } else if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                        isMediaBrowserService = true;
                    }
                }
            }

            if (isMediaBrowserService) {
                mediaBrowserServices.add(component);
            }
        }

        if (!mediaBrowserServices.isEmpty() && !hasPlayFromSearch) {
            String message = "Missing MEDIA_PLAY_FROM_SEARCH intent-filter: "
                    + "add an `<intent-filter>` containing "
                    + "`<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` "
                    + "to an `<activity>` or `<service>` to support voice searches on Android Auto.";
            for (Element service : mediaBrowserServices) {
                context.report(MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                        service,
                        context.getElementLocation(service),
                        message);
            }
        }
    }

    private static Element getFirstChildElementByTagName(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && tagName.equals(((Element) node).getTagName())) {
                return (Element) node;
            }
        }
        return null;
    }
}