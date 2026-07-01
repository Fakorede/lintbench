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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class AndroidAutoDetector extends ResourceXmlDetector {

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    private static final Collection<String> VALID_NAMES =
            Collections.unmodifiableCollection(Arrays.asList("media", "notification", "sms"));

    public static final Issue ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `<uses>` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the `name` attribute. Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || !(parent instanceof Element)) {
            return;
        }

        if (!TAG_AUTOMOTIVE_APP.equals(((Element) parent).getTagName())) {
            return;
        }

        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty() || !VALID_NAMES.contains(name)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Invalid `name` attribute for `<uses>` element. Valid values are `media`, `notification`, or `sms`."
            );
        }
    }
}