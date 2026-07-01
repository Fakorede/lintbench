package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization " +
            "if it includes a Leanback launcher intent filter. The banner is the " +
            "app launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.NODE_CATEGORY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
            Node parent = element.getParentNode();
            if (parent instanceof Element) {
                Element intentFilter = (Element) parent;
                if (SdkConstants.NODE_INTENT_FILTER.equals(intentFilter.getTagName())) {
                    Node grandParent = intentFilter.getParentNode();
                    if (grandParent instanceof Element) {
                        Element activity = (Element) grandParent;
                        String tagName = activity.getTagName();
                        if (SdkConstants.NODE_ACTIVITY.equals(tagName)
                                || SdkConstants.NODE_ACTIVITY_ALIAS.equals(tagName)) {
                            if (!activity.hasAttributeNS(SdkConstants.ANDROID_URI, "banner")) {
                                Node grandGrandParent = activity.getParentNode();
                                if (grandGrandParent instanceof Element) {
                                    Element application = (Element) grandGrandParent;
                                    if (SdkConstants.NODE_APPLICATION.equals(application.getTagName())) {
                                        if (!application.hasAttributeNS(SdkConstants.ANDROID_URI, "banner")) {
                                            context.report(
                                                    ISSUE,
                                                    activity,
                                                    context.getNameLocation(activity),
                                                    "Expect `android:banner` with `LEANBACK_LAUNCHER` activity"
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}