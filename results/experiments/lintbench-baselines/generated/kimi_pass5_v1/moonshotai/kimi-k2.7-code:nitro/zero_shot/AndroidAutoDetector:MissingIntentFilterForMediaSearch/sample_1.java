package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_MANIFEST;
import static com.android.SdkConstants.TAG_METADATA;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String AUTO_METADATA_NAME = "com.google.android.gms.car.application";

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an "
                    + "`intent-filter` for the action "
                    + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`. To do this, add "
                    + "`<intent-filter><action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />"
                    + "</intent-filter>` to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasMediaPlayFromSearch;
    private boolean mHasAutoTarget;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_MANIFEST);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            mHasMediaPlayFromSearch = false;
            mHasAutoTarget = false;
            mApplicationElement = null;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        inspectElement(element);
    }

    private void inspectElement(@NonNull Element element) {
        String tag = element.getTagName();

        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        } else if (TAG_METADATA.equals(tag)) {
            if (AUTO_METADATA_NAME.equals(getAndroidName(element))) {
                mHasAutoTarget = true;
            }
        } else if (TAG_SERVICE.equals(tag) || TAG_ACTIVITY.equals(tag)) {
            if (hasIntentFilterAction(element, MEDIA_BROWSER_SERVICE)) {
                mHasAutoTarget = true;
            }
        } else if (TAG_INTENT_FILTER.equals(tag)) {
            if (hasAction(element, MEDIA_PLAY_FROM_SEARCH)) {
                mHasMediaPlayFromSearch = true;
            }
        }

        for (Element child : getChildElements(element)) {
            inspectElement(child);
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        if (mHasAutoTarget && !mHasMediaPlayFromSearch && mApplicationElement != null) {
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Add an intent-filter for android.media.action.MEDIA_PLAY_FROM_SEARCH "
                            + "to support voice searches on Android Auto.");
        }
    }

    private static boolean hasIntentFilterAction(@NonNull Element component, @NonNull String action) {
        for (Element child : getChildElements(component)) {
            if (TAG_INTENT_FILTER.equals(child.getTagName()) && hasAction(child, action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAction(@NonNull Element intentFilter, @NonNull String action) {
        for (Element child : getChildElements(intentFilter)) {
            if (TAG_ACTION.equals(child.getTagName())
                    && action.equals(getAndroidName(child))) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String getAndroidName(@NonNull Element element) {
        String value = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute(ATTR_NAME);
        }
        return value != null ? value : "";
    }

    @NonNull
    private static List<Element> getChildElements(@NonNull Element parent) {
        List<Element> children = new ArrayList<>();
        Node node = parent.getFirstChild();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) node);
            }
            node = node.getNextSibling();
        }
        return children;
    }
}