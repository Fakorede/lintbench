package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class AndroidAutoDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "MissingMediaBrowserServiceIntentFilter",
        "Missing MediaBrowserService intent-filter",
        "An Automotive Media App requires an exported service that extends " +
        "`android.service.media.MediaBrowserService` with an `intent-filter` for the action " +
        "`android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
        "To do this, add\n" +
        "`<intent-filter>`\n" +
        "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n" +
        "`</intent-filter>`\n" +
        "to the service that extends `android.service.media.MediaBrowserService`",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.MANIFEST))
    );

    private static final String ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String AUTOMOTIVE_FEATURE = "android.hardware.type.automotive";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isAutomotiveApp(context)) {
            return;
        }

        String exported = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_EXPORTED);
        if (!"true".equals(exported)) {
            return;
        }

        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        if (!name.contains("MediaBrowserService")) {
            return;
        }

        if (hasMediaBrowserIntentFilter(element)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
            "MediaBrowserService must have an intent-filter with action " + ACTION_MEDIA_BROWSER_SERVICE);
    }

    private boolean isAutomotiveApp(XmlContext context) {
        if (context.document == null) return false;
        Element root = context.document.getDocumentElement();
        if (root == null) return false;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (SdkConstants.TAG_USES_FEATURE.equals(child.getTagName())) {
                    String featureName = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    String required = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                    if (AUTOMOTIVE_FEATURE.equals(featureName) && !"false".equals(required)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasMediaBrowserIntentFilter(Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (SdkConstants.TAG_INTENT_FILTER.equals(child.getTagName())) {
                    if (hasAction(child, ACTION_MEDIA_BROWSER_SERVICE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasAction(Element intentFilter, String actionName) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (SdkConstants.TAG_ACTION.equals(child.getTagName())) {
                    String name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (actionName.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}