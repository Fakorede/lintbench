package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean hasLeanbackFeature = false;
        boolean hasLeanbackLauncher = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                String tag = childEl.getTagName();

                if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
                    String name = childEl.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("android.software.leanback".equals(name)) {
                        hasLeanbackFeature = true;
                    }
                } else if (SdkConstants.TAG_ACTIVITY.equals(tag) || SdkConstants.TAG_ACTIVITY_ALIAS.equals(tag)) {
                    NodeList intentFilters = childEl.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
                    for (int j = 0; j < intentFilters.getLength(); j++) {
                        Element filter = (Element) intentFilters.item(j);
                        NodeList categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
                        for (int k = 0; k < categories.getLength(); k++) {
                            Element cat = (Element) categories.item(k);
                            String catName = cat.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                            if ("android.intent.category.LEANBACK_LAUNCHER".equals(catName)) {
                                hasLeanbackLauncher = true;
                                break;
                            }
                        }
                        if (hasLeanbackLauncher) break;
                    }
                }
            }
            if (hasLeanbackFeature && hasLeanbackLauncher) break;
        }

        if (hasLeanbackFeature && !hasLeanbackLauncher) {
            context.report(ISSUE, context.getLocation(element),
                    "Expecting `android.intent.category.LEANBACK_LAUNCHER` intent filter for TV app");
        }
    }
}