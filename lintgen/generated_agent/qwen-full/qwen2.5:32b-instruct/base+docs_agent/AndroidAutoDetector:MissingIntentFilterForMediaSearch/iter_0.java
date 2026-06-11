package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ScreenSize;
import com.android.resources.ScreenOrientation;
import com.android.resources.Version;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.android.utils.ILogger;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.EnumSet;
import java.util.Set;

public class AndroidAutoDetector extends XmlScanner {

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @NonNull
    @Override
    public Set<? extends Class<? extends Element>> getApplicableElements() {
        return XmlScanner.applicableElements(SdkConstants.ELEMENT_ACTIVITY,
                SdkConstants.ELEMENT_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!hasMediaPlayFromSearchIntentFilter(context, element)) {
            context.report(this, element, context.getLocation(element),
                    "Missing intent-filter for android.media.action.MEDIA_PLAY_FROM_SEARCH");
        }
    }

    private boolean hasMediaPlayFromSearchIntentFilter(@NonNull XmlContext context,
                                                       @NonNull Element element) {
        for (Element child : XmlUtils.getChildrenByTagName(element, SdkConstants.ELEMENT_INTENT_FILTER)) {
            if (hasAction(child, ACTION_MEDIA_PLAY_FROM_SEARCH)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAction(@NonNull Element intentFilter,
                              @NonNull String actionName) {
        for (Element child : XmlUtils.getChildrenByTagName(intentFilter, SdkConstants.ELEMENT_ACTION)) {
            Attr nameAttr = XmlUtils.getAttribute(child, "android:name");
            if (nameAttr != null && ACTION_MEDIA_PLAY_FROM_SEARCH.equals(nameAttr.getValue())) {
                return true;
            }
        }
        return false;
    }

    public static class IssueInfo extends IssueRegistry.IssueInfo {
        @NonNull
        @Override
        public String getId() {
            return "MissingMediaPlayFromSearchIntentFilter";
        }

        @NonNull
        @Override
        public String getCategory() {
            return Category.CORRECTNESS;
        }

        @NonNull
        @Override
        public String getName() {
            return "Missing MEDIA_PLAY_FROM_SEARCH intent-filter";
        }

        @NonNull
        @Override
        public String getDescription() {
            return "To support voice searches on Android Auto, you should also register an `intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. "
                    + "Add `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` to your `<activity>` or `<service>`. ";
        }

        @NonNull
        @Override
        public String getExplanationUrl() {
            return "https://developer.android.com/training/auto/audio/index.html#support_voice";
        }

        @NonNull
        @Override
        public Severity getDefaultSeverity() {
            return Severity.WARNING;
        }
    }
}