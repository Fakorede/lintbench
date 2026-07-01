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
import org.jetbrains.annotations.Nullable;
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
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        boolean isTvApp = false;
        boolean hasLeanbackLauncher = false;

        NodeList manifestChildren = element.getChildNodes();
        for (int i = 0; i < manifestChildren.getLength(); i++) {
            Node node = manifestChildren.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String tag = child.getTagName();
                if ("uses-feature".equals(tag)) {
                    String name = child.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                    if ("android.software.leanback".equals(name) ||
                        "android.hardware.type.television".equals(name)) {
                        isTvApp = true;
                    }
                } else if ("application".equals(tag)) {
                    if (hasLeanbackLauncherInApplication(child)) {
                        hasLeanbackLauncher = true;
                    }
                }
            }
        }

        if (isTvApp && !hasLeanbackLauncher) {
            context.report(ISSUE, element, context.getLocation(element),
                    "The manifest declares a TV feature but does not include an activity with the " +
                    "`android.intent.category.LEANBACK_LAUNCHER` intent filter.");
        }
    }

    private boolean hasLeanbackLauncherInApplication(@NotNull Element application) {
        NodeList appChildren = application.getChildNodes();
        for (int i = 0; i < appChildren.getLength(); i++) {
            Node node = appChildren.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String tag = child.getTagName();
                if ("activity".equals(tag) || "activity-alias".equals(tag)) {
                    if (hasLeanbackLauncherInComponent(child)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackLauncherInComponent(@NotNull Element component) {
        NodeList children = component.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if ("intent-filter".equals(child.getTagName())) {
                    if (hasLeanbackCategory(child)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackCategory(@NotNull Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if ("category".equals(child.getTagName())) {
                    String name = child.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                    if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}