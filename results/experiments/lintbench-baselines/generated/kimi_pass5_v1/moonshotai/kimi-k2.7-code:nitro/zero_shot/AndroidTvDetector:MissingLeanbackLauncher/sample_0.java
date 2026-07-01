package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.CATEGORY_LEANBACK_LAUNCHER;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

    private static final String TV_FEATURE = "android.hardware.type.television";

    private static final Implementation IMPLEMENTATION = new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE);

    public static final Issue MISSING_LEANBACK_LAUNCHER = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity for TV "
                    + "using an `android.intent.category.LEANBACK_LAUNCHER` intent filter.\n"
                    + "Reference: https://developer.android.com/training/tv/start/start.html#tv-activity",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isTelevisionApp(context.document.getDocumentElement())) {
            return;
        }

        if (!hasLeanbackLauncherActivity(element)) {
            context.report(
                    MISSING_LEANBACK_LAUNCHER,
                    element,
                    context.getLocation(element),
                    "Missing Leanback Launcher intent filter. TV apps must add an "
                            + "intent filter with category LEANBACK_LAUNCHER to a launcher activity.");
        }
    }

    private static boolean isTelevisionApp(@Nullable Element root) {
        if (root == null) {
            return false;
        }
        NodeList features = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            if (TV_FEATURE.equals(feature.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                String required = feature.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                return required.isEmpty() || Boolean.parseBoolean(required);
            }
        }
        return false;
    }

    private static boolean hasLeanbackLauncherActivity(@NonNull Element application) {
        NodeList activities = application.getElementsByTagName(TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList filters = activity.getElementsByTagName(TAG_INTENT_FILTER);
            for (int j = 0; j < filters.getLength(); j++) {
                Element filter = (Element) filters.item(j);
                NodeList categories = filter.getElementsByTagName(TAG_CATEGORY);
                for (int k = 0; k < categories.getLength(); k++) {
                    Element category = (Element) categories.item(k);
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(
                            category.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}