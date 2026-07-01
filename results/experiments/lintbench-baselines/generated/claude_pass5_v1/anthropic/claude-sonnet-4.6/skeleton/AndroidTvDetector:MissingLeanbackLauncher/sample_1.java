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
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";

    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

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
                    IMPLEMENTATION);

    /** Whether the current file is an Android manifest */
    private boolean mIsManifest;

    /** Whether we found a leanback launcher intent filter */
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = ANDROID_MANIFEST_XML.equals(context.file.getName());
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsManifest) {
            return;
        }
        if (!mHasLeanbackLauncher) {
            context.report(
                    ISSUE,
                    context.getLocation(context.document.getDocumentElement()),
                    "Leanback apps should have a launcher `Activity` that handles "
                            + "`android.intent.category.LEANBACK_LAUNCHER`");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }

        // Look through intent-filter children for LEANBACK_LAUNCHER category
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                continue;
            }

            // Check if this intent-filter has ACTION_MAIN and CATEGORY_LEANBACK_LAUNCHER
            boolean hasActionMain = false;
            boolean hasLeanbackCategory = false;

            NodeList filterChildren = childElement.getChildNodes();
            for (int j = 0; j < filterChildren.getLength(); j++) {
                Node filterChild = filterChildren.item(j);
                if (filterChild.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element filterChildElement = (Element) filterChild;
                String tagName = filterChildElement.getTagName();
                String nameAttr = filterChildElement.getAttributeNS(ANDROID_NS, ATTR_NAME);

                if (TAG_ACTION.equals(tagName) && ACTION_MAIN.equals(nameAttr)) {
                    hasActionMain = true;
                } else if (TAG_CATEGORY.equals(tagName) && CATEGORY_LEANBACK_LAUNCHER.equals(nameAttr)) {
                    hasLeanbackCategory = true;
                }
            }

            if (hasActionMain && hasLeanbackCategory) {
                mHasLeanbackLauncher = true;
                return;
            }
        }
    }
}