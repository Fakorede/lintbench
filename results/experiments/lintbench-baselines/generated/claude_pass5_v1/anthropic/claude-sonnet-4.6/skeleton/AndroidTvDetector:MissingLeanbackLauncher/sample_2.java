package com.android.tools.lint.checks;

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

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTION = "action";

    private static final String ATTR_NAME = "name";

    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity "
                            + "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` "
                            + "intent filter.\n\n"
                            + "To fix this, add an intent filter with both `android.intent.action.MAIN` "
                            + "and `android.intent.category.LEANBACK_LAUNCHER` to one of your activities.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Whether we are currently processing a manifest file */
    private boolean mIsManifest;

    /** Whether we found a leanback launcher intent filter */
    private boolean mHasLeanbackLauncher;

    /** The application element, used for reporting */
    private Element mApplicationElement;

    /** The XmlContext for the current file */
    private XmlContext mXmlContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = context.file.getName().equals(ANDROID_MANIFEST_XML);
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
        mXmlContext = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsManifest) {
            return;
        }

        if (!mHasLeanbackLauncher && mApplicationElement != null && mXmlContext != null) {
            mXmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    mXmlContext.getLocation(mApplicationElement),
                    "Expecting an `<intent-filter>` with `action` `android.intent.action.MAIN` and "
                            + "`category` `android.intent.category.LEANBACK_LAUNCHER`");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }

        mXmlContext = context;

        // Cache the application element for reporting
        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (TAG_APPLICATION.equals(parentElement.getTagName())) {
                mApplicationElement = parentElement;
            }
        }

        // Check if this activity has a leanback launcher intent filter
        if (TAG_ACTIVITY.equals(element.getTagName())) {
            NodeList intentFilters = element.getElementsByTagName(TAG_INTENT_FILTER);
            for (int i = 0; i < intentFilters.getLength(); i++) {
                Node intentFilterNode = intentFilters.item(i);
                if (intentFilterNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element intentFilter = (Element) intentFilterNode;

                boolean hasMainAction = false;
                boolean hasLeanbackCategory = false;

                NodeList children = intentFilter.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element childElement = (Element) child;
                    String tagName = childElement.getTagName();
                    String nameAttr = childElement.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (nameAttr == null || nameAttr.isEmpty()) {
                        nameAttr = childElement.getAttribute("android:" + ATTR_NAME);
                    }

                    if (TAG_ACTION.equals(tagName) && ACTION_MAIN.equals(nameAttr)) {
                        hasMainAction = true;
                    } else if (TAG_CATEGORY.equals(tagName)
                            && CATEGORY_LEANBACK_LAUNCHER.equals(nameAttr)) {
                        hasLeanbackCategory = true;
                    }
                }

                if (hasMainAction && hasLeanbackCategory) {
                    mHasLeanbackLauncher = true;
                    return;
                }
            }
        }
    }
}