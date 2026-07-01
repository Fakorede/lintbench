package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "MissingLeanbackLauncher",
        "Missing Leanback Launcher Intent Filter",
        "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.\n" +
        "Reference: https://developer.android.com/training/tv/start/start.html#tv-activity",
        Category.CORRECTNESS,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList categories = element.getElementsByTagName(TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Node node = categories.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element cat = (Element) node;
                String name = cat.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (LEANBACK_LAUNCHER.equals(name)) {
                    return;
                }
            }
        }
        context.report(ISSUE, element, context.getLocation(element),
            "Expecting `android.intent.category.LEANBACK_LAUNCHER` intent filter for TV support");
    }
}