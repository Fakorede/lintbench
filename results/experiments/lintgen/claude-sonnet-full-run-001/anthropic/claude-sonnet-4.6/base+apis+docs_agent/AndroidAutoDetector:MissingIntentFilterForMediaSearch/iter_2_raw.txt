package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    public static final Issue MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH = Issue.create(
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
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ANDROID_AUTO_META_DATA = "com.google.android.gms.car.application";
    private static final String ANDROID_AUTO_META_DATA_2 = "com.google.android.gms.car.service";

    // Track whether we've seen a MediaBrowserService and whether we've seen MEDIA_PLAY_FROM_SEARCH
    private boolean mHasMediaBrowserService = false;
    private boolean mHasMediaPlayFromSearch = false;
    private boolean mHasAndroidAutoMetaData = false;
    private Element mMediaBrowserServiceElement = null;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE, TAG_ACTIVITY, TAG_META_DATA);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Reset state for each document
        mHasMediaBrowserService = false;
        mHasMediaPlayFromSearch = false;
        mHasAndroidAutoMetaData = false;
        mMediaBrowserServiceElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if (TAG_META_DATA.equals(tagName)) {
            String name = getAndroidName(element);
            if (ANDROID_AUTO_META_DATA.equals(name) || ANDROID_AUTO_META_DATA_2.equals(name)) {
                mHasAndroidAutoMetaData = true;
            }
            return;
        }

        // Check for MediaBrowserService intent filter
        if (TAG_SERVICE.equals(tagName) || TAG_ACTIVITY.equals(tagName)) {
            if (hasIntentFilterWithAction(element, ACTION_MEDIA_BROWSER_SERVICE)) {
                mHasMediaBrowserService = true;
                if (mMediaBrowserServiceElement == null) {
                    mMediaBrowserServiceElement = element;
                }
            }
            if (hasIntentFilterWithAction(element, ACTION_MEDIA_PLAY_FROM_SEARCH)) {
                mHasMediaPlayFromSearch = true;
            }
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        // After processing the entire manifest (when we finish the root element),
        // check if we need to report
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            if (mHasMediaBrowserService && !mHasMediaPlayFromSearch) {
                Element reportElement = mMediaBrowserServiceElement;
                if (reportElement != null) {
                    context.report(
                            MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
                            reportElement,
                            context.getLocation(reportElement),
                            "Missing `intent-filter` for action " +
                            "`android.media.action.MEDIA_PLAY_FROM_SEARCH`"
                    );
                }
            }
        }
    }

    private boolean hasIntentFilterWithAction(Element element, String actionName) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (hasAction(childElement, actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAction(Element intentFilterElement, String actionName) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_ACTION.equals(childElement.getTagName())) {
                String name = getAndroidName(childElement);
                if (actionName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getAndroidName(Element element) {
        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        return name != null ? name : "";
    }
}