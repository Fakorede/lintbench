package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String aliasType = element.getAttribute("type");
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        String value = LintUtils.getText(element);
        if (value == null) {
            return;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return;
        }

        char firstChar = value.charAt(0);
        if (firstChar != '@' && firstChar != '?') {
            return;
        }

        String ref = value.substring(1);
        if (ref.startsWith("+")) {
            ref = ref.substring(1);
        }

        int colonIndex = ref.indexOf(':');
        if (colonIndex != -1) {
            ref = ref.substring(colonIndex + 1);
        }

        int slashIndex = ref.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String refType = ref.substring(0, slashIndex);

        if (!aliasType.equals(refType)) {
            String message = String.format(
                    "Resource alias type \"%1$s\" does not match referenced resource type \"%2$s\"",
                    aliasType, refType);
            context.report(ISSUE, context.getLocation(element), message);
        }
    }
}