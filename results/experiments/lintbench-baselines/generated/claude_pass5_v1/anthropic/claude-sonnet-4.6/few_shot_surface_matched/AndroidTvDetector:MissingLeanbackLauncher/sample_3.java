package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String USES_FEATURE_LEANBACK =
            "android.software.leanback";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher "
                            + "activity for TV in its manifest using a "
                            + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncherActivity;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncherActivity = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mHasLeanbackFeature && !mHasLeanbackLauncherActivity) {
            XmlContext xmlContext = (XmlContext) context;
            Node reportNode = mApplicationElement != null
                    ? mApplicationElement
                    : xmlContext.document.getDocumentElement();
            xmlContext.report(
                    ISSUE,
                    reportNode,
                    xmlContext.getLocation(reportNode),
                    "Leanback feature declared but no activity with a `"
                            + LEANBACK_LAUNCHER_CATEGORY
                            + "` intent filter was found.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (NODE_USES_FEATURE.equals(tagName)) {
            String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (USES_FEATURE_LEANBACK.equals(featureName)) {
                mHasLeanbackFeature = true;
            }
        } else if (NODE_ACTIVITY.equals(tagName)) {
            // Keep track of the application element for reporting purposes
            Node parent = element.getParentNode();
            if (parent instanceof Element && mApplicationElement == null) {
                mApplicationElement = (Element) parent;
            }

            if (activityHasLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncherActivity = true;
            }
        }
    }

    private boolean activityHasLeanbackLauncherIntentFilter(@NonNull Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && NODE_INTENT.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                if (intentFilterHasLeanbackLauncherCategory(intentFilter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackLauncherCategory(@NonNull Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && NODE_CATEGORY.equals(child.getNodeName())) {
                Element categoryElement = (Element) child;
                String categoryName = categoryElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(categoryName)) {
                    return true;
                }
            }
        }
        return false;
    }
}