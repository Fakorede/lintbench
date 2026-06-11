package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import java.util.Set;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @NonNull
    @Override
    public Set<String> getApplicableElements() {
        return Set.of(SdkConstants.ELEMENT_ACTIVITY, SdkConstants.ELEMENT_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!hasMediaPlayFromSearchIntentFilter(context, element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing intent-filter for android.media.action.MEDIA_PLAY_FROM_SEARCH"
            );
        }
    }

    private boolean hasMediaPlayFromSearchIntentFilter(@NonNull XmlContext context,
                                                       @NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeName().equals(SdkConstants.ELEMENT_INTENT_FILTER)) {
                if (hasAction((Element) child, ACTION_MEDIA_PLAY_FROM_SEARCH)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAction(@NonNull Element intentFilter,
                              @NonNull String actionName) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeName().equals(SdkConstants.ELEMENT_ACTION)) {
                String nameAttrValue = child.getAttribute(ResourceType.MANIFEST.getPrefix() + ":name");
                if (nameAttrValue != null && ACTION_MEDIA_PLAY_FROM_SEARCH.equals(nameAttrValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static final Issue ISSUE = Issue.create(
            "MissingMediaPlayFromSearchIntentFilter",
            "To support voice searches on Android Auto, you should also register an `intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. "
                    + "Add `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` to your `<activity>` or `<service>`. ",
            "This issue reports any activities or services that are missing the required intent-filter.",
            Category.CORRECTNESS,
            6, // Priority
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );
}