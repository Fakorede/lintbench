package com.android.tools.lint.checks;

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
            "if it includes a Leanback launcher intent filter. The banner is the app " +
            "launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_NAME = "name";
    private static final String VALUE_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("category");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (VALUE_LEANBACK_LAUNCHER.equals(name)) {
            Node intentFilter = element.getParentNode();
            if (intentFilter instanceof Element && "intent-filter".equals(intentFilter.getNodeName())) {
                Node activity = intentFilter.getParentNode();
                if (activity instanceof Element) {
                    Element activityElement = (Element) activity;
                    if (activityElement.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
                        return;
                    }

                    Node application = activityElement.getParentNode();
                    if (application instanceof Element && "application".equals(application.getNodeName())) {
                        Element appElement = (Element) application;
                        if (appElement.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
                            return;
                        }
                    }
                }
            }

            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Expect `android:banner` to be defined in the manifest for Leanback launcher activity"
            );
        }
    }
}