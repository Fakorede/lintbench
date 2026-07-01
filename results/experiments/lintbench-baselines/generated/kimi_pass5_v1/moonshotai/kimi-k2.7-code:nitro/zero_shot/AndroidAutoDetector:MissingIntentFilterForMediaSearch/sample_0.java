package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_META_DATA;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String AUTO_APP_METADATA_NAME = "com.google.android.gms.car.application";
    private static final String AUTO_HARDWARE_FEATURE = "android.hardware.type.automotive";

    public static final Issue MISSING_INTENT_FILTER_MEDIA_SEARCH = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an "
                    + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    private boolean mHasMediaSearch;
    private boolean mIsAutoApp;
    @Nullable private Element mApplicationElement;

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_ACTIVITY,
                TAG_SERVICE,
                TAG_USES_FEATURE,
                TAG_META_DATA
        );
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasMediaSearch = false;
        mIsAutoApp = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        } else if (TAG_USES_FEATURE.equals(tag)) {
            if (AUTO_HARDWARE_FEATURE.equals(getAndroidAttribute(element, ATTR_NAME))) {
                mIsAutoApp = true;
            }
        } else if (TAG_META_DATA.equals(tag)) {
            if (TAG_APPLICATION.equals(element.getParentNode().getNodeName())
                    && AUTO_APP_METADATA_NAME.equals(getAndroidAttribute(element, ATTR_NAME))) {
                mIsAutoApp = true;
            }
        } else if (TAG_ACTIVITY.equals(tag) || TAG_SERVICE.equals(tag)) {
            if (hasPlayFromSearchFilter(element)) {
                mHasMediaSearch = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsAutoApp && !mHasMediaSearch && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    MISSING_INTENT_FILTER_MEDIA_SEARCH,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Add an `<intent-filter>` with action `android.media.action.MEDIA_PLAY_FROM_SEARCH` "
                            + "to an `<activity>` or `<service>` to support voice searches on Android Auto."
            );
        }
    }

    private static boolean hasPlayFromSearchFilter(@NonNull Element component) {
        NodeList children = component.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                NodeList filterChildren = child.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE
                            && TAG_ACTION.equals(filterChild.getNodeName())) {
                        String name = getAndroidAttribute((Element) filterChild, ATTR_NAME);
                        if (MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    private static String getAndroidAttribute(@NonNull Element element, @NonNull String localName) {
        if (element.hasAttributeNS(ANDROID_URI, localName)) {
            return element.getAttributeNS(ANDROID_URI, localName);
        }
        return null;
    }
}