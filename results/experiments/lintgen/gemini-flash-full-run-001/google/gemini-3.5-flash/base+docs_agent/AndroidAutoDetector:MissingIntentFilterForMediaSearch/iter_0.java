package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collection;
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
            "To do this, add `<intent-filter><action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" /></intent-filter>` " +
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
    public Collection<Scope> getApplicableFiles() {
        return Scope.MANIFEST_SCOPE;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        Element application = getFirstSubTagByName(root, "application");
        if (application == null) {
            return;
        }

        List<Element> mediaBrowserServices = new ArrayList<>();
        boolean hasMediaPlayFromSearch = false;

        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String tagName = element.getTagName();
                if ("service".equals(tagName)) {
                    if (hasIntentFilterAction(element, "android.media.browse.MediaBrowserService")) {
                        mediaBrowserServices.add(element);
                    }
                }
                if ("service".equals(tagName) || "activity".equals(tagName)) {
                    if (hasIntentFilterAction(element, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                        hasMediaPlayFromSearch = true;
                    }
                }
            }
        }

        if (!mediaBrowserServices.isEmpty() && !hasMediaPlayFromSearch) {
            for (Element service : mediaBrowserServices) {
                context.report(
                        ISSUE,
                        service,
                        context.getNameLocation(service),
                        "Missing `MEDIA_PLAY_FROM_SEARCH` intent-filter"
                );
            }
        }
    }

    private boolean hasIntentFilterAction(Element element, String actionName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                        Element action = (Element) filterChild;
                        String name = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                        if (actionName.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private Element getFirstSubTagByName(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }
}