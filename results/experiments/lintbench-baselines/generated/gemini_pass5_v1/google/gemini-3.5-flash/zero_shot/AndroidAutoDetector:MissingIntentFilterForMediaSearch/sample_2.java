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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an " +
            "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. " +
            "To do this, add \n" +
            "`<intent-filter>`\n" +
            "    `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />`\n" +
            "`</intent-filter>`\n" +
            "to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        List<Element> mediaBrowserServices = new ArrayList<>();
        boolean hasMediaPlayFromSearch = false;

        NodeList services = root.getElementsByTagName(SdkConstants.TAG_SERVICE);
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            if (hasIntentFilterAction(service, "android.media.browse.MediaBrowserService")) {
                mediaBrowserServices.add(service);
            }
            if (hasIntentFilterAction(service, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                hasMediaPlayFromSearch = true;
            }
        }

        NodeList activities = root.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (hasIntentFilterAction(activity, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                hasMediaPlayFromSearch = true;
            }
        }

        if (!mediaBrowserServices.isEmpty() && !hasMediaPlayFromSearch) {
            for (Element service : mediaBrowserServices) {
                context.report(
                        ISSUE,
                        service,
                        context.getNameLocation(service),
                        "Missing `intent-filter` for Action `android.media.action.MEDIA_PLAY_FROM_SEARCH`"
                );
            }
        }
    }

    private boolean hasIntentFilterAction(Element element, String actionName) {
        for (Element filter : getChildrenByTagName(element, SdkConstants.TAG_INTENT_FILTER)) {
            for (Element action : getChildrenByTagName(filter, SdkConstants.TAG_ACTION)) {
                String name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (actionName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Element> getChildrenByTagName(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList nodeList = parent.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (tagName.equals(element.getTagName())) {
                    children.add(element);
                }
            }
        }
        return children;
    }
}