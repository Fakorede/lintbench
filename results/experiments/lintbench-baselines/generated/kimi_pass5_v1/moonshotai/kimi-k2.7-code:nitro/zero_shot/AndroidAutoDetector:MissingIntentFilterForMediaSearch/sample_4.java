package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AndroidAutoDetector extends ResourceXmlDetector {

    private static final String TAG_META_DATA = "meta-data";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_ACTION = "action";

    private static final String ANDROID_AUTO_METADATA_NAME = "com.google.android.gms.car.application";
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an intent-filter "
                    + "for the action android.media.action.MEDIA_PLAY_FROM_SEARCH. To do this, "
                    + "add <intent-filter><action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />"
                    + "</intent-filter> to your <activity> or <service>.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE),
            "https://developer.android.com/training/auto/audio/index.html#support_voice"
    );

    private boolean mIsAutoApp;
    private boolean mHasMediaPlayFromSearch;
    private Element mAutoAppElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_META_DATA, TAG_USES_FEATURE, TAG_ACTION);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mIsAutoApp = false;
        mHasMediaPlayFromSearch = false;
        mAutoAppElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_META_DATA.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (ANDROID_AUTO_METADATA_NAME.equals(name)) {
                mIsAutoApp = true;
                mAutoAppElement = element;
            }
        } else if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (FEATURE_AUTOMOTIVE.equals(name)) {
                mIsAutoApp = true;
                if (mAutoAppElement == null) {
                    mAutoAppElement = element;
                }
            }
        } else if (TAG_ACTION.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                mHasMediaPlayFromSearch = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mIsAutoApp && !mHasMediaPlayFromSearch) {
            Element reportElement = mAutoAppElement;
            if (reportElement != null) {
                Element parent = (Element) reportElement.getParentNode();
                if (parent != null) {
                    reportElement = parent;
                }
            }
            if (reportElement == null) {
                reportElement = context.getDocument().getDocumentElement();
            }

            context.report(
                    ISSUE,
                    reportElement,
                    context.getLocation(reportElement),
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter"
            );
        }
    }
}