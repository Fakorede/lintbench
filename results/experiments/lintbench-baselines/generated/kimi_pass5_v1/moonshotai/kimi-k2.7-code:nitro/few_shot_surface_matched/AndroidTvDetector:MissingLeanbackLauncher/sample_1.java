package com.android.tools.lint.checks;

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
                    "An application intended to run on TV devices must declare a launcher "
                            + "activity for TV in its manifest using an "
                            + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String HARDWARE_TYPE_TELEVISION = "android.hardware.type.television";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String NODE_USES_FEATURE = "uses-feature";
    private static final String NODE_ACTIVITY = "activity";
    private static final String NODE_ACTIVITY_ALIAS = "activity-alias";
    private static final String NODE_INTENT_FILTER = "intent-filter";
    private static final String NODE_ACTION = "action";
    private static final String NODE_CATEGORY = "category";

    private boolean mHasTelevisionFeature;
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_ACTIVITY, NODE_ACTIVITY_ALIAS);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasTelevisionFeature = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasTelevisionFeature && !mHasLeanbackLauncher) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            xmlContext.report(
                    ISSUE,
                    root,
                    xmlContext.getLocation(root),
                    "Missing Leanback Launcher intent filter. Apps intended to run on TV "
                            + "devices must declare a launcher activity with an "
                            + "android.intent.category.LEANBACK_LAUNCHER intent filter.");
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (NODE_USES_FEATURE.equals(tag)) {
            String name = getAttribute(element, "name");
            String required = getAttribute(element, "required");
            if (HARDWARE_TYPE_TELEVISION.equals(name)
                    && (required.isEmpty() || Boolean.parseBoolean(required))) {
                mHasTelevisionFeature = true;
            }
        } else if (NODE_ACTIVITY.equals(tag) || NODE_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    private static boolean hasLeanbackLauncher(Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!NODE_INTENT_FILTER.equals(childElement.getTagName())) {
                continue;
            }
            boolean hasMain = false;
            boolean hasLeanback = false;
            NodeList grandchildren = childElement.getChildNodes();
            for (int j = 0; j < grandchildren.getLength(); j++) {
                Node grandchild = grandchildren.item(j);
                if (grandchild.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element grandchildElement = (Element) grandchild;
                String name = getAttribute(grandchildElement, "name");
                if (NODE_ACTION.equals(grandchildElement.getTagName())
                        && ACTION_MAIN.equals(name)) {
                    hasMain = true;
                } else if (NODE_CATEGORY.equals(grandchildElement.getTagName())
                        && CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanback = true;
                }
            }
            if (hasMain && hasLeanback) {
                return true;
            }
        }
        return false;
    }

    private static String getAttribute(Element element, String localName) {
        String value = element.getAttribute("android:" + localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute(localName);
        }
        return value;
    }
}