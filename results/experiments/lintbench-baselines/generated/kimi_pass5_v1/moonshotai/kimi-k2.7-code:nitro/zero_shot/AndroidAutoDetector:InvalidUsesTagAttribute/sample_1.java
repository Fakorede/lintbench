package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends ResourceXmlDetector {

    private static final String ISSUE_ID = "InvalidUsesTagAttribute";

    private static final Collection<String> VALID_NAMES = Collections.unmodifiableCollection(
            Arrays.asList("media", "notification", "sms")
    );

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            "Invalid name attribute for uses element",
            "The `<uses>` element inside `<automotiveApp>` must specify a valid value for the `name` attribute. "
                    + "Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (!"uses".equals(element.getTagName())) {
            return;
        }

        Element parent = (Element) element.getParentNode();
        if (parent == null || !"automotiveApp".equals(parent.getTagName())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || !VALID_NAMES.contains(value)) {
            context.report(
                    ISSUE,
                    context.getValueLocation(attribute),
                    "Invalid name attribute value \"" + value + "\" for <uses>. "
                            + "Valid values are: media, notification, sms."
            );
        }
    }
}