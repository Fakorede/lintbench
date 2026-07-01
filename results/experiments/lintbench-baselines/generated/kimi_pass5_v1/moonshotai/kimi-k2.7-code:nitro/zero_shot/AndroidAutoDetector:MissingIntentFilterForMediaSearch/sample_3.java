package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should register an intent-filter "
                    + "for the action android.media.action.MEDIA_PLAY_FROM_SEARCH.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasMatchingAction(element, MEDIA_PLAY_FROM_SEARCH)) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Missing intent-filter for action android.media.action.MEDIA_PLAY_FROM_SEARCH"
            );
        }
    }

    private static boolean hasMatchingAction(Element root, String actionName) {
        for (int i = 0; i < root.getElementsByTagName(TAG_ACTION).getLength(); i++) {
            Element action = (Element) root.getElementsByTagName(TAG_ACTION).item(i);
            if (actionName.equals(action.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                return true;
            }
        }
        return false;
    }
}