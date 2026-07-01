package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT_FILTER;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

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

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity for TV "
                            + "in its manifest using an `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String FEATURE_TV = "android.hardware.type.television";
    private static final String FEATURE_LEANBACK = "android.software.leanback";

    private boolean mRequiresTv;
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_ACTIVITY, NODE_ACTIVITY_ALIAS);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mRequiresTv = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mRequiresTv || mHasLeanbackLauncher) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        Element application =
                (Element) xmlContext.document.getElementsByTagName(NODE_APPLICATION).item(0);
        if (application != null) {
            xmlContext.report(
                    ISSUE,
                    application,
                    xmlContext.getLocation(application),
                    "TV apps must declare an activity with an intent filter containing the "
                            + "LEANBACK_LAUNCHER category");
        } else {
            Element manifest = xmlContext.document.getDocumentElement();
            xmlContext.report(
                    ISSUE,
                    manifest,
                    xmlContext.getLocation(manifest),
                    "TV apps must declare an activity with an intent filter containing the "
                            + "LEANBACK_LAUNCHER category");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NODE_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (FEATURE_TV.equals(name) || FEATURE_LEANBACK.equals(name)) {
                // The required attribute defaults to true when absent.
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (required.isEmpty() || Boolean.parseBoolean(required)) {
                    mRequiresTv = true;
                }
            }
        } else if (NODE_ACTIVITY.equals(tag) || NODE_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private static boolean hasLeanbackLauncherIntentFilter(@NonNull Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!NODE_INTENT_FILTER.equals(child.getNodeName())) {
                continue;
            }
            Element intentFilter = (Element) child;
            NodeList categories = intentFilter.getElementsByTagName(NODE_CATEGORY);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}