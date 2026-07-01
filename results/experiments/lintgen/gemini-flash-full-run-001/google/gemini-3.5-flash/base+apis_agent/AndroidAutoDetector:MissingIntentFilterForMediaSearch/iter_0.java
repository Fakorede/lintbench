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
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements XmlScanner {

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
        Element application = null;
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && "application".equals(child.getNodeName())) {
                application = (Element) child;
                break;
            }
        }
        if (application == null) {
            return;
        }

        List<Element> mediaBrowserServices = new ArrayList<>();
        boolean hasPlayFromSearch = false;

        for (Node child = application.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String tagName = child.getNodeName();
            if ("service".equals(tagName)) {
                Element service = (Element) child;
                if (hasIntentFilterAction(service, "android.media.browse.MediaBrowserService")) {
                    mediaBrowserServices.add(service);
                }
                if (hasIntentFilterAction(service, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                    hasPlayFromSearch = true;
                }
            } else if ("activity".equals(tagName)) {
                Element activity = (Element) child;
                if (hasIntentFilterAction(activity, "android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
                    hasPlayFromSearch = true;
                }
            }
        }

        if (!mediaBrowserServices.isEmpty() && !hasPlayFromSearch) {
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
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                for (Node filterChild = intentFilter.getFirstChild(); filterChild != null; filterChild = filterChild.getNextSibling()) {
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                        Element action = (Element) filterChild;
                        String name = action.getAttributeNS(SdkConstants.ANDROID_URI, "name");
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