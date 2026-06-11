package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Category;

public class AndroidTvDetector extends Detector implements XmlScanner {
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";

    @NonNull
    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTIVITY);
    }

    @Override
    public Issue getIssue() {
        return ISSUE;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.getNodeName().equals(TAG_ACTIVITY)) {
            List<Element> intentFilters = context.getXmlDocument().getElementsByTagName(TAG_INTENT_FILTER);
            boolean hasLauncherCategory = false;

            for (int i = 0; i < intentFilters.getLength(); i++) {
                Element intentFilter = (Element) intentFilters.item(i);
                List<Element> actions = context.getXmlDocument().getElementsByTagName(TAG_ACTION);

                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String nameAttrValue = action.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME);
                    if (nameAttrValue != null && nameAttrValue.equals(CATEGORY_LEANBACK_LAUNCHER)) {
                        hasLauncherCategory = true;
                        break;
                    }
                }

                if (hasLauncherCategory) {
                    break;
                }
            }

            if (!hasLauncherCategory) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Activity is missing android.intent.category.LEANBACK_LAUNCHER intent filter");
            }
        }
    }

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncherIntentFilter",
            "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using an `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            "TV applications should have at least one activity with the `android.intent.category.LEANBACK_LAUNCHER` category in order to be properly launched on TV devices.",
            Category.CORRECTNESS,
            5, Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));
}