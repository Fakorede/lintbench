package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT_FILTER;
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
                    "An application intended to run on TV devices must declare a launcher "
                            + "activity for TV in its manifest using an "
                            + "android.intent.category.LEANBACK_LAUNCHER intent filter.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ATTR_NAME = "name";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    private boolean mHasLeanbackLauncher;
    private Element mApplicationElement;
    private boolean mRequiresLeanback;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_ACTIVITY, NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
        mRequiresLeanback = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NODE_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        } else if (NODE_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, "required");
                mRequiresLeanback = required.isEmpty() || Boolean.parseBoolean(required);
            }
        } else if (NODE_ACTIVITY.equals(tag)) {
            if (hasLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mRequiresLeanback && !mHasLeanbackLauncher && mApplicationElement != null) {
            context.report(
                    ISSUE,
                    mApplicationElement,
                    context.getElementLocation(mApplicationElement),
                    "Missing Leanback Launcher Intent Filter");
        }
    }

    private static boolean hasLeanbackLauncherIntentFilter(@NonNull Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && NODE_INTENT_FILTER.equals(((Element) child).getTagName())) {
                Element filter = (Element) child;
                if (hasLeanbackLauncherCategory(filter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasLeanbackLauncherCategory(@NonNull Element filter) {
        NodeList children = filter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (NODE_CATEGORY.equals(element.getTagName())
                        && LEANBACK_LAUNCHER.equals(
                                element.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                    return true;
                }
            }
        }
        return false;
    }
}