package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AndroidAutoDetector extends ResourceXmlDetector {
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";
    private static final Set<String> VALID_NAMES =
            new HashSet<>(Arrays.asList("media", "notification", "sms"));

    public static final Issue INVALID_USES_TAG_ATTRIBUTE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` must use a valid value for the `name` attribute. "
                    + "Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_XML_SCOPE)
    ).addMoreInfo("https://developer.android.com/training/auto/start/index.html#auto-metadata");

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_AUTOMOTIVE_APP.equals(element.getParentNode().getNodeName())) {
            return;
        }

        Attr attr = element.getAttributeNode(ATTR_NAME);
        if (attr == null) {
            attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, ATTR_NAME);
        }

        String name = attr != null ? attr.getValue() : null;
        if (name == null || name.isEmpty() || !VALID_NAMES.contains(name)) {
            Location location = attr != null
                    ? context.getLocation(attr)
                    : context.getLocation(element);
            String current = name == null || name.isEmpty() ? "missing" : "`" + name + "`";
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    element,
                    location,
                    "Invalid `name` attribute value (" + current
                            + "). Expected `media`, `notification`, or `sms`.");
        }
    }
}