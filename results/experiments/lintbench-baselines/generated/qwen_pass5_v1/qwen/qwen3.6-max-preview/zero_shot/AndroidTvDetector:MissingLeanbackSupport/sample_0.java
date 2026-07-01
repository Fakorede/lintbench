package com.android.tools.lint.checks;

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

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_MANIFEST;
import static com.android.SdkConstants.TAG_USES_FEATURE;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by Android TV.\n\n" +
                    "To fix this, add\n" +
                    "`<uses-feature android:name=\"android.software.leanback\"\n" +
                    "               android:required=\"false\" />`\n" +
                    "to your manifest.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NodeList children = element.getChildNodes();
        boolean hasLeanback = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_USES_FEATURE.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if ("android.software.leanback".equals(name)) {
                        hasLeanback = true;
                        break;
                    }
                }
            }
        }

        if (!hasLeanback) {
            context.report(ISSUE, context.getLocation(element),
                    "The manifest should declare the use of the Leanback user interface required by Android TV.");
        }
    }
}