package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String TELEVISION_FEATURE = "android.hardware.type.television";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity "
                    + "for TV in its manifest using an `android.intent.category.LEANBACK_LAUNCHER` "
                    + "intent filter. Refer to the Android TV documentation for more information.",
            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        if (!declaresTvSupport(element.getParentNode())) {
            return;
        }

        boolean hasLeanbackLauncher = false;
        NodeList appChildren = element.getChildNodes();
        for (int i = 0; i < appChildren.getLength(); i++) {
            Node appChild = appChildren.item(i);
            if (appChild.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) appChild;
            String tag = child.getTagName();
            if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
                if (isLeanbackLauncher(child)) {
                    hasLeanbackLauncher = true;
                    break;
                }
            }
        }

        if (!hasLeanbackLauncher) {
            Location location = context.getLocation(element);
            context.report(
                    ISSUE,
                    location,
                    "Missing `android.intent.category.LEANBACK_LAUNCHER` intent filter for a TV application");
        }
    }

    private boolean declaresTvSupport(Node manifestNode) {
        if (manifestNode == null || !(manifestNode instanceof Element)) {
            return false;
        }
        NodeList children = manifestNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            if (TAG_USES_FEATURE.equals(child.getTagName())) {
                String name = child.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (LEANBACK_FEATURE.equals(name) || TELEVISION_FEATURE.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLeanbackLauncher(Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            if (!TAG_INTENT_FILTER.equals(child.getTagName())) {
                continue;
            }

            boolean hasMain = false;
            boolean hasLeanback = false;
            NodeList filterChildren = child.getChildNodes();
            for (int j = 0; j < filterChildren.getLength(); j++) {
                Node filterNode = filterChildren.item(j);
                if (filterNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element filterChild = (Element) filterNode;
                String tag = filterChild.getTagName();
                if (TAG_ACTION.equals(tag)) {
                    String name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (ACTION_MAIN.equals(name)) {
                        hasMain = true;
                    }
                } else if (TAG_CATEGORY.equals(tag)) {
                    String name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        hasLeanback = true;
                    }
                }
            }

            if (hasMain && hasLeanback) {
                return true;
            }
        }
        return false;
    }
}