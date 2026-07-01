package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_INTENT_ACTION_MAIN;
import static com.android.SdkConstants.ANDROID_INTENT_CATEGORY_LEANBACK_LAUNCHER;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String FEATURE_TELEVISION = "android.hardware.type.television";
    private static final String FEATURE_LEANBACK = "android.software.leanback";

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity "
                    + "for TV in its manifest using an `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasTelevisionFeature;
    private boolean mHasLeanbackLauncher;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasTelevisionFeature = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_ACTIVITY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (TAG_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (FEATURE_TELEVISION.equals(name) || FEATURE_LEANBACK.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (required.isEmpty() || Boolean.parseBoolean(required)) {
                    mHasTelevisionFeature = true;
                }
            }
        } else if (TAG_ACTIVITY.equals(tagName)) {
            if (isLeanbackLauncherActivity(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mHasTelevisionFeature || mHasLeanbackLauncher || !(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        Element root = xmlContext.document.getDocumentElement();
        if (root == null) {
            return;
        }

        Element application = getApplicationElement(root);
        Location location = application != null
                ? xmlContext.getLocation(application)
                : xmlContext.getLocation(root);

        xmlContext.report(
                ISSUE,
                location,
                "Missing `LEANBACK_LAUNCHER` intent filter for TV application");
    }

    private static boolean isLeanbackLauncherActivity(@NonNull Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!TAG_INTENT_FILTER.equals(node.getNodeName())) {
                continue;
            }
            Element intentFilter = (Element) node;
            boolean hasMain = false;
            boolean hasLeanback = false;
            NodeList filterChildren = intentFilter.getChildNodes();
            for (int j = 0; j < filterChildren.getLength(); j++) {
                Node childNode = filterChildren.item(j);
                if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element child = (Element) childNode;
                String childTag = child.getTagName();
                if (TAG_ACTION.equals(childTag)) {
                    String actionName = child.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (ANDROID_INTENT_ACTION_MAIN.equals(actionName)) {
                        hasMain = true;
                    }
                } else if (TAG_CATEGORY.equals(childTag)) {
                    String categoryName = child.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (ANDROID_INTENT_CATEGORY_LEANBACK_LAUNCHER.equals(categoryName)) {
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

    @Nullable
    private static Element getApplicationElement(@NonNull Element root) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && TAG_APPLICATION.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }
}