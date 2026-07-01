package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The <uses-feature> element should not require this unsupported TV hardware"
                            + " feature. Any <uses-feature> not explicitly marked with"
                            + " required=\"false\" is necessary on the device to be installed on."
                            + " Ensure that any features that might prevent it from being installed"
                            + " on a TV device are reviewed and marked as not required in the"
                            + " manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
    }

    @Override
    public void afterCheckFile(XmlContext context) {
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = getAttributeValue(element, "name");
        if (name == null || name.isEmpty() || !isUnsupportedTvFeature(name)) {
            return;
        }

        String required = getAttributeValue(element, "required");
        if ("false".equals(required)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "The feature \""
                        + name
                        + "\" is not supported on TV devices. If you declare this feature, set"
                        + " required=\"false\".");
    }

    private static boolean isUnsupportedTvFeature(String name) {
        switch (name) {
            case "android.hardware.telephony":
            case "android.hardware.touchscreen":
            case "android.hardware.faketouch":
            case "android.hardware.faketouch.multitouch.jazzhand":
            case "android.hardware.faketouch.multitouch.distinct":
            case "android.hardware.screen.portrait":
                return true;
            default:
                return false;
        }
    }

    private static String getAttributeValue(org.w3c.dom.Element element, String localName) {
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            int length = attributes.getLength();
            for (int i = 0; i < length; i++) {
                org.w3c.dom.Node attr = attributes.item(i);
                String attrLocalName = attr.getLocalName();
                if (attrLocalName == null) {
                    String nodeName = attr.getNodeName();
                    if (nodeName != null && nodeName.endsWith(":" + localName)) {
                        return attr.getNodeValue();
                    }
                } else if (localName.equals(attrLocalName)) {
                    return attr.getNodeValue();
                }
            }
        }
        return null;
    }
}