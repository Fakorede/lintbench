package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements XmlScanner {

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";
    private static final Set<String> VALID_NAMES =
            Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList("media", "notification", "sms")));

    static final Issue ISSUE =
            Issue.create(
                    "InvalidUsesTagAttribute",
                    "Invalid name attribute for `<uses>` element",
                    "The `<uses>` element inside `<automotiveApp>` must have a `name` attribute "
                            + "with one of the following values: `media`, `notification`, or `sms`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null || !TAG_AUTOMOTIVE_APP.equals(root.getTagName())) {
            return;
        }

        Attr attr = element.getAttributeNode(ATTR_NAME);
        String name = attr != null ? attr.getValue() : null;

        if (name == null || name.isEmpty()) {
            context.report(
                    ISSUE,
                    attr != null ? context.getValueLocation(attr) : context.getLocation(element),
                    "The `<uses>` element must specify a `name` attribute");
            return;
        }

        if (!VALID_NAMES.contains(name)) {
            context.report(
                    ISSUE,
                    context.getValueLocation(attr),
                    "Invalid `name` attribute value `"
                            + name
                            + "`. Must be one of: media, notification, sms.");
        }
    }
}