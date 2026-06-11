package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String SERVICE_TAG = "service";
    private static final String INTENT_FILTER_TAG = "intent-filter";
    private static final String ACTION_TAG = "action";
    private static final String NAME_ATTRIBUTE = "android:name";

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Automotive Media App requires an exported service that extends `android.service.media.MediaBrowserService` with an `intent-filter` for the action `android.media.browse.MediaBrowserService`.",
            "To be able to browse and play media, add `<intent-filter><action android:name=\"android.media.browse.MediaBrowserService\" /></intent-filter>` to the service that extends `android.service.media.MediaBrowserService`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, true));

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of(SERVICE_TAG);
    }

    @Nullable
    @Override
    public Void visitElement(@NonNull XmlContext context, @NonNull Element element) throws IOException, SAXException {
        if (!isMediaBrowserService(element)) {
            return null;
        }
        boolean hasIntentFilter = false;

        for (int i = 0; i < element.getChildNodes().getLength(); i++) {
            org.w3c.dom.Node child = element.getChildNodes().item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && INTENT_FILTER_TAG.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                for (int j = 0; j < intentFilter.getChildNodes().getLength(); j++) {
                    org.w3c.dom.Node actionNode = intentFilter.getChildNodes().item(j);
                    if (ACTION_TAG.equals(actionNode.getNodeName()) && isMediaBrowserServiceAction(XmlUtils.getAttribute((Element) actionNode, NAME_ATTRIBUTE))) {
                        hasIntentFilter = true;
                        break;
                    }
                }
            }
        }

        if (!hasIntentFilter) {
            context.report(ISSUE, element, context.getLocation(element), "Missing intent-filter for android.media.browse.MediaBrowserService");
        }
        return null;
    }

    private boolean isMediaBrowserService(@NonNull Element element) {
        String className = XmlUtils.getAttribute(element, "android:name");
        if (className == null || !className.endsWith("MediaBrowserService")) {
            return false;
        }
        String exported = XmlUtils.getAttribute(element, "android:exported");
        if (exported == null || !Boolean.parseBoolean(exported)) {
            return false;
        }
        return true;
    }

    private boolean isMediaBrowserServiceAction(@NonNull String actionName) {
        return "android.media.browse.MediaBrowserService".equals(actionName);
    }

    public static class Factory implements Detector.Factory {

        @NonNull
        @Override
        public AndroidAutoDetector create(@NonNull Context context) {
            return new AndroidAutoDetector();
        }
    }
}