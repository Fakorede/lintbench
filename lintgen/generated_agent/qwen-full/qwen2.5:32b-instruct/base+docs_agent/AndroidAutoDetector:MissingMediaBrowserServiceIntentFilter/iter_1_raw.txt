package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String SERVICE_TAG = "service";
    private static final String INTENT_FILTER_TAG = "intent-filter";
    private static final String ACTION_TAG = "action";
    private static final String NAME_ATTRIBUTE = "android:name";

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return ImmutableList.of(SERVICE_TAG);
    }

    @Nullable
    @Override
    public InspectionResult visitElement(@NonNull XmlContext context, @NonNull Element element) throws IOException, SAXException {
        if (!isMediaBrowserService(element)) {
            return null;
        }
        boolean hasIntentFilter = false;

        for (int i = 0; i < element.getChildNodes().getLength(); i++) {
            Node child = element.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && INTENT_FILTER_TAG.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                for (int j = 0; j < intentFilter.getChildNodes().getLength(); j++) {
                    Node actionNode = intentFilter.getChildNodes().item(j);
                    if (ACTION_TAG.equals(actionNode.getNodeName()) && isMediaBrowserServiceAction(XmlUtils.getAttribute((Element) actionNode, NAME_ATTRIBUTE))) {
                        hasIntentFilter = true;
                        break;
                    }
                }
            }
        }

        if (!hasIntentFilter) {
            return InspectionResult.error("Missing intent-filter for android.media.browse.MediaBrowserService")
                    .at(context.getLocation(element))
                    .build();
        }
        return null;
    }

    private boolean isMediaBrowserService(@NonNull Element element) {
        String className = XmlUtils.getAttribute(element, "android:exported");
        if (className == null || !Boolean.parseBoolean(className)) {
            return false;
        }
        className = XmlUtils.getAttribute(element, NAME_ATTRIBUTE);
        if (className == null) {
            return false;
        }
        return className.endsWith(SdkConstants.CLASS_MEDIA_BROWSER_SERVICE)
                || className.contains("." + SdkConstants.CLASS_MEDIA_BROWSER_SERVICE);
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