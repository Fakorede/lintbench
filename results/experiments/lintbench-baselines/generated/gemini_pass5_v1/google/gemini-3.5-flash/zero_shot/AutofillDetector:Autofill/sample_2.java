package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "Autofill",
            "Align with Autofill",
            "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                    + "higher or explicitly specify that the view is not important for autofill.",
            Category.COMPLIANCE,
            3,
            Severity.WARNING,
            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getProject().getTargetSdkVersion().getFeatureLevel() < 26) {
            return;
        }

        String tagName = element.getTagName();
        boolean isEditText = false;
        if (tagName.equals(SdkConstants.VIEW)) {
            String clazz = element.getAttribute(SdkConstants.ATTR_CLASS);
            if (clazz != null && (clazz.endsWith("EditText") || clazz.endsWith("AutoCompleteTextView"))) {
                isEditText = true;
            }
        } else {
            int index = tagName.lastIndexOf('.');
            String simpleName = index != -1 ? tagName.substring(index + 1) : tagName;
            if (simpleName.equals("EditText") ||
                simpleName.equals("AutoCompleteTextView") ||
                simpleName.equals("TextInputEditText")) {
                isEditText = true;
            }
        }

        if (!isEditText) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "autofillHints")) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, "importantForAutofill")) {
            return;
        }

        // Check if any parent has importantForAutofill="noExcludeDescendants"
        Node parent = element.getParentNode();
        while (parent instanceof Element) {
            Element parentElement = (Element) parent;
            String important = parentElement.getAttributeNS(SdkConstants.ANDROID_URI, "importantForAutofill");
            if ("noExcludeDescendants".equals(important)) {
                return;
            }
            parent = parent.getParentNode();
        }

        context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Missing `autofillHints` attribute"
        );
    }
}