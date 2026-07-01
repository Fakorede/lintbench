package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;
import static com.android.xml.AndroidManifest.NODE_USES_SDK;

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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String HARDWARE_FEATURE_LEANBACK = "android.hardware.type.television";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity"
                            + " for TV in its manifest using a"
                            + " `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackLauncherActivity;
    private boolean mHasLeanbackFeature;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_ACTIVITY, NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncherActivity = false;
        mHasLeanbackFeature = false;
        mManifestElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackFeature && !mHasLeanbackLauncherActivity && mManifestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mManifestElement,
                    xmlContext.getLocation(mManifestElement),
                    "Manifest should contain a `<activity>` tag with a"
                            + " `android.intent.category.LEANBACK_LAUNCHER` intent filter");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        // Track the manifest element for reporting location
        if (mManifestElement == null) {
            Node parent = element.getParentNode();
            while (parent != null) {
                if (parent.getNodeType() == Node.ELEMENT_NODE) {
                    String parentName = parent.getNodeName();
                    if ("manifest".equals(parentName)) {
                        mManifestElement = (Element) parent;
                        break;
                    }
                }
                parent = parent.getParentNode();
            }
            if (mManifestElement == null && element.getOwnerDocument() != null) {
                mManifestElement = element.getOwnerDocument().getDocumentElement();
            }
        }

        if (NODE_USES_FEATURE.equals(tagName)) {
            String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (HARDWARE_FEATURE_LEANBACK.equals(featureName)) {
                mHasLeanbackFeature = true;
            }
        } else if (NODE_ACTIVITY.equals(tagName)) {
            if (activityHasLeanbackLauncher(element)) {
                mHasLeanbackLauncherActivity = true;
            }
        }
    }

    private boolean activityHasLeanbackLauncher(@NonNull Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && NODE_INTENT.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                if (intentFilterHasLeanbackLauncher(intentFilter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && NODE_CATEGORY.equals(child.getNodeName())) {
                Element category = (Element) child;
                String categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(categoryName)) {
                    return true;
                }
            }
        }
        return false;
    }
}