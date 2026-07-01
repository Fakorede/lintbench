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
import java.util.EnumSet;
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
            "To do this, add\n" +
            "<intent-filter>\n" +
            "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n" +
            "</intent-filter>\n" +
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
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.MANIFEST_SCOPE;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        Element application = null;
        NodeList rootChildren = root.getChildNodes();
        for (int i = 0; i < rootChildren.getLength(); i++) {
            Node child = rootChildren.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "application".equals(child.getNodeName())) {
                application = (Element) child;
                break;
            }
        }
        if (application == null) {
            return;
        }

        Element mediaBrowserService = null;
        boolean hasPlayFromSearch = false;

        NodeList appChildren = application.getChildNodes();
        for (int i = 0; i < appChildren.getLength(); i++) {
            Node child = appChildren.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String nodeName = child.getNodeName();
                if ("service".equals(nodeName)) {
                    Element service = (Element) child;
                    if (hasAction(service, "android.media.browse.MediaBrowserService")) {
                        mediaBrowserService = service;
                    }
                    if (hasAction(service, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                        hasPlayFromSearch = true;
                    }
                } else if ("activity".equals(nodeName)) {
                    Element activity = (Element) child;
                    if (hasAction(activity, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                        hasPlayFromSearch = true;
                    }
                }
            }
        }

        if (mediaBrowserService != null && !hasPlayFromSearch) {
            context.report(
                    ISSUE,
                    mediaBrowserService,
                    context.getNameLocation(mediaBrowserService),
                    "Missing `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent-filter"
            );
        }
    }

    private static boolean hasAction(Element element, String actionName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                NodeList filterChildren = child.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                        Element actionElement = (Element) filterChild;
                        String name = actionElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if (actionName.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}