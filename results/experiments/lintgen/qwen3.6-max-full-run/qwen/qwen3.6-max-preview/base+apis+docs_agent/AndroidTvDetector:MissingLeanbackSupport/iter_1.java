package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by Android TV.\n\n" +
            "To fix this, add\n" +
            "```xml\n" +
            "<uses-feature android:name=\"android.software.leanback\"\n" +
            "              android:required=\"false\" />\n" +
            "```\n" +
            "to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, EnumSet.of(Scope.MANIFEST))
    );

    private static final String LEANBACK_FEATURE = "android.software.leanback";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean hasLeanback = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (SdkConstants.TAG_USES_FEATURE.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (LEANBACK_FEATURE.equals(name)) {
                        hasLeanback = true;
                        break;
                    }
                }
            }
        }

        if (!hasLeanback) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Manifest does not declare the Leanback feature required for Android TV");
        }
    }
}