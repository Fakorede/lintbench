package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each "
                            + "localization if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home "
                            + "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"category".equals(element.getTagName())) {
            return;
        }
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
            Node parentNode = element.getParentNode();
            if (parentNode instanceof Element && "intent-filter".equals(parentNode.getNodeName())) {
                Element intentFilter = (Element) parentNode;
                Node activityNode = intentFilter.getParentNode();
                if (activityNode instanceof Element) {
                    Element activity = (Element) activityNode;
                    String activityTagName = activity.getTagName();
                    if ("activity".equals(activityTagName) || "activity-alias".equals(activityTagName)) {
                        if (activity.hasAttributeNS(ANDROID_URI, "banner")) {
                            return;
                        }
                        Node applicationNode = activity.getParentNode();
                        if (applicationNode instanceof Element && "application".equals(applicationNode.getNodeName())) {
                            Element application = (Element) applicationNode;
                            if (application.hasAttributeNS(ANDROID_URI, "banner")) {
                                return;
                            }
                        }
                        context.report(
                                ISSUE,
                                element,
                                context.getLocation(element),
                                "Expects `android:banner` to be defined in the manifest"
                        );
                    }
                }
            }
        }
    }
}