package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TV_FEATURE_HARDWARE = "android.hardware.type.television";
    private static final String TV_FEATURE_SOFTWARE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher intent filter",
            "Apps that are intended to run on TV devices must declare a launcher activity "
                    + "with an intent filter that includes the "
                    + "android.intent.category.LEANBACK_LAUNCHER category.",
            Category.CORRECTNESS,
            8,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element manifest) {
        boolean tvApp = declaresTvFeature(manifest);
        boolean hasLeanbackLauncher = hasLeanbackLauncher(manifest);

        if (tvApp && !hasLeanbackLauncher) {
            context.report(
                    ISSUE,
                    manifest,
                    context.getElementLocation(manifest),
                    "TV app is missing a launcher activity with the LEANBACK_LAUNCHER category");
        }
    }

    private boolean declaresTvFeature(Element manifest) {
        NodeList children = manifest.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !SdkConstants.TAG_USES_FEATURE.equals(child.getNodeName())) {
                continue;
            }

            Element usesFeature = (Element) child;
            String name =
                    usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (TV_FEATURE_HARDWARE.equals(name) || TV_FEATURE_SOFTWARE.equals(name)) {
                String required =
                        usesFeature.getAttributeNS(
                                SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if (required.isEmpty() || SdkConstants.VALUE_TRUE.equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackLauncher(Element manifest) {
        NodeList applications = manifest.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        for (int i = 0; i < applications.getLength(); i++) {
            Element application = (Element) applications.item(i);
            NodeList activities =
                    application.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
            for (int j = 0; j < activities.getLength(); j++) {
                Element activity = (Element) activities.item(j);
                NodeList filters =
                        activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
                for (int k = 0; k < filters.getLength(); k++) {
                    Element filter = (Element) filters.item(k);
                    NodeList categories =
                            filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
                    for (int c = 0; c < categories.getLength(); c++) {
                        Element category = (Element) categories.item(c);
                        String categoryName =
                                category.getAttributeNS(
                                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if (LEANBACK_LAUNCHER_CATEGORY.equals(categoryName)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}