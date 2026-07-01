package com.android.tools.lint.checks;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements XmlScanner {
    private static final Set<String> VALID_NAMES = new HashSet<>(Arrays.asList("media", "notification", "sms"));

    public static final Issue ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || !"automotiveApp".equals(parent.getNodeName())) {
            return;
        }

        if (!element.hasAttribute("name")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing required `name` attribute for `<uses>` element");
            return;
        }

        String name = element.getAttribute("name");
        if (!VALID_NAMES.contains(name)) {
            context.report(ISSUE, element, context.getLocation(element.getAttributeNode("name")),
                    "Invalid value for `name` attribute. Must be one of: media, notification, sms");
        }
    }
}