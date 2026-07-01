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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback support declaration",
            "Android TV apps should declare the `android.software.leanback` feature "
                    + "in the manifest. Add `<uses-feature android:name=\"android.software.leanback\" "
                    + "android:required=\"false\" />` to your AndroidManifest.xml.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.ANDROID_MANIFEST_XML.equals(context.file.getName())) {
            return;
        }

        boolean hasLeanback = false;
        NodeList children = element.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element usesFeature = (Element) node;
            String name = usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            String required = usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            if ("android.software.leanback".equals(name) && "false".equals(required)) {
                hasLeanback = true;
                break;
            }
        }

        if (!hasLeanback) {
            context.report(
                    MISSING_LEANBACK_SUPPORT,
                    context.getElementLocation(element),
                    "Missing `android.software.leanback` feature declaration; add "
                            + "`<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to the manifest."
            );
        }
    }

    public static Collection<Issue> getIssues() {
        return Collections.singletonList(MISSING_LEANBACK_SUPPORT);
    }
}