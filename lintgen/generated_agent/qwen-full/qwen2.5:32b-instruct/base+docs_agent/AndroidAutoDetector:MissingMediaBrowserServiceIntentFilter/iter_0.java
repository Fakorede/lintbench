package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ScreenSize;
import com.android.resources.ScreenOrientation;
import com.android.resources.SmallestScreenWidth;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.ILogger;
import com.android.utils.PositionXmlParser;
import com.android.utils.XmlPullAttributes;
import com.google.common.collect.ImmutableList;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
    public InspectionResult visitElement(@NonNull XmlContext context, @NonNull PositionXmlParser xml) throws IOException, SAXException {
        Element service = (Element) xml.getDocument().getDocumentElement();
        if (!isMediaBrowserService(service)) {
            return null;
        }
        boolean hasIntentFilter = false;
        for (Node child = service.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && INTENT_FILTER_TAG.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                for (Attr attr : XmlUtils.getAttributes(intentFilter)) {
                    if (ACTION_TAG.equals(attr.getName()) && isMediaBrowserServiceAction(attr.getValue())) {
                        hasIntentFilter = true;
                        break;
                    }
                }
            }
        }
        if (!hasIntentFilter) {
            return InspectionResult.error("Missing intent-filter for android.media.browse.MediaBrowserService")
                    .at(xml.getLine(), xml.getColumn())
                    .build();
        }
        return null;
    }

    private boolean isMediaBrowserService(@NonNull Element element) {
        String className = XmlUtils.getAttribute(element, NAME_ATTRIBUTE);
        if (className == null) {
            return false;
        }
        return className.endsWith(SdkConstants.CLASS_MEDIA_BROWSER_SERVICE)
                || className.contains("." + SdkConstants.CLASS_MEDIA_BROWSER_SERVICE);
    }

    private boolean isMediaBrowserServiceAction(@NonNull String actionName) {
        return actionName.equals("android.media.browse.MediaBrowserService");
    }

    public static class Factory implements Detector.Factory {

        @NonNull
        @Override
        public AndroidAutoDetector create(@NonNull Context context) {
            return new AndroidAutoDetector();
        }
    }
}