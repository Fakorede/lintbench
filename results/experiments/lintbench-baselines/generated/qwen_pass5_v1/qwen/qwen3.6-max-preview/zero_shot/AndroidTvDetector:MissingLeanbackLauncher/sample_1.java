package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingLeanbackLauncher",
        "Missing Leanback Launcher Intent Filter",
        "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
        Category.USABILITY,
        6,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getProject().isLibrary()) {
            return;
        }

        boolean hasLeanbackLauncher = false;

        NodeList applications = element.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        for (int i = 0; i < applications.getLength(); i++) {
            Element application = (Element) applications.item(i);
            NodeList activities = application.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
            for (int j = 0; j < activities.getLength(); j++) {
                Element activity = (Element) activities.item(j);
                NodeList intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
                for (int k = 0; k < intentFilters.getLength(); k++) {
                    Element intentFilter = (Element) intentFilters.item(k);
                    NodeList categories = intentFilter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
                    for (int l = 0; l < categories.getLength(); l++) {
                        Element category = (Element) categories.item(l);
                        String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                            hasLeanbackLauncher = true;
                            break;
                        }
                    }
                    if (hasLeanbackLauncher) break;
                }
                if (hasLeanbackLauncher) break;
            }
            if (hasLeanbackLauncher) break;
        }

        if (!hasLeanbackLauncher) {
            context.report(ISSUE, element, context.getLocation(element),
                "Expecting `android.intent.category.LEANBACK_LAUNCHER` intent filter for TV apps");
        }
    }
}