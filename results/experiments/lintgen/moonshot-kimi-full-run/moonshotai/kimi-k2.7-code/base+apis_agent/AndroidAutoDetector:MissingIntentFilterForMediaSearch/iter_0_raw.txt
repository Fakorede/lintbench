package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.EnumSet;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should register an "
                    + "<code>intent-filter</code> for the action "
                    + "<code>android.media.action.MEDIA_PLAY_FROM_SEARCH</code>. "
                    + "Add the filter to an <code>&lt;activity&gt;</code> or "
                    + "<code>&lt;service&gt;</code> in your manifest.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.MANIFEST_SCOPE))
    );

    private static final String MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    @Override
    public Collections<? extends String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasMediaPlayFromSearchFilter(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter for Android Auto voice search"
            );
        }
    }

    private boolean hasMediaPlayFromSearchFilter(Element root) {
        return checkElement(root);
    }

    private boolean checkElement(Element element) {
        if (SdkConstants.TAG_INTENT_FILTER.equals(element.getTagName())) {
            Node parentNode = element.getParentNode();
            if (parentNode instanceof Element) {
                Element parent = (Element) parentNode;
                String parentTag = parent.getTagName();
                if (SdkConstants.TAG_ACTIVITY.equals(parentTag)
                        || SdkConstants.TAG_SERVICE.equals(parentTag)
                        || SdkConstants.TAG_ACTIVITY_ALIAS.equals(parentTag)) {
                    NodeList children = element.getChildNodes();
                    for (int i = 0, n = children.getLength(); i < n; i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() == Node.ELEMENT_NODE
                                && SdkConstants.TAG_ACTION.equals(((Element) child).getTagName())) {
                            Element action = (Element) child;
                            String name = action.getAttributeNS(SdkConstants.ANDROID_URI,
                                    SdkConstants.ATTR_NAME);
                            if (name.isEmpty()) {
                                name = action.getAttribute(SdkConstants.ATTR_NAME);
                            }
                            if (MEDIA_PLAY_FROM_SEARCH.equals(name)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (checkElement((Element) child)) {
                    return true;
                }
            }
        }

        return false;
    }
}