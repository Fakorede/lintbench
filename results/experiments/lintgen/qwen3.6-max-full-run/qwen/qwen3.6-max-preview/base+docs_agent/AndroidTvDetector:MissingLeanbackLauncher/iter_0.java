package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        boolean hasTvFeature = false;
        boolean hasLeanbackLauncher = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                String tag = childEl.getTagName();
                if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
                    String name = childEl.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                        String required = childEl.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                        if (!"false".equals(required)) {
                            hasTvFeature = true;
                        }
                    }
                } else if (SdkConstants.TAG_APPLICATION.equals(tag)) {
                    if (hasLeanbackLauncherInApplication(childEl)) {
                        hasLeanbackLauncher = true;
                    }
                }
            }
        }

        if (hasTvFeature && !hasLeanbackLauncher) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Expecting `android.intent.category.LEANBACK_LAUNCHER` intent filter for TV app");
        }
    }

    private boolean hasLeanbackLauncherInApplication(@NotNull Element application) {
        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tag = ((Element) child).getTagName();
                if (SdkConstants.TAG_ACTIVITY.equals(tag) || "activity-alias".equals(tag)) {
                    if (hasLeanbackLauncherInActivity((Element) child)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackLauncherInActivity(@NotNull Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_INTENT_FILTER.equals(((Element) child).getTagName())) {
                NodeList filterChildren = ((Element) child).getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_CATEGORY.equals(((Element) filterChild).getTagName())) {
                        String name = ((Element) filterChild).getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}