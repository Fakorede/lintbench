package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {
    private static final String TAG_USES = "uses";
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String ATTR_NAME = "name";
    private static final Collection<String> VALID_NAMES =
            Arrays.asList("media", "notification", "sms");

    public static final Issue INVALID_USES_TAG_ATTRIBUTE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid name attribute for uses element",
            "The <uses> element in <automotiveApp> should contain a valid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_AUTOMOTIVE_APP.equals(element.getParentNode().getNodeName())) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty() || !VALID_NAMES.contains(name)) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    element,
                    context.getLocation(element),
                    "Invalid name attribute value \"" + name + "\" for <uses>; must be one of: media, notification, sms"
            );
        }
    }
}