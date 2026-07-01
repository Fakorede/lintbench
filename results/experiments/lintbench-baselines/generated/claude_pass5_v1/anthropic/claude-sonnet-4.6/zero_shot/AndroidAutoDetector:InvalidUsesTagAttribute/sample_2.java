package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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
import java.util.Collections;

/**
 * Detector for Android Auto metadata XML files.
 * Checks that the {@code <uses>} element within {@code <automotiveApp>} has a valid
 * {@code name} attribute value.
 */
public class AndroidAutoDetector extends ResourceXmlDetector {

    /** Valid values for the name attribute of the uses element */
    private static final String[] VALID_USES_NAMES = {"media", "notification", "sms", "template"};

    /** The automotiveApp root element */
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";

    /** The uses element */
    private static final String TAG_USES = "uses";

    /** The name attribute */
    private static final String ATTR_NAME = "name";

    public static final Issue INVALID_USES_TAG_ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the " +
            "`name` attribute. Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.RESOURCE_FILE_SCOPE))
            .addMoreInfo(
                    "https://developer.android.com/training/auto/start/index.html#auto-metadata");

    /** Constructs a new {@link AndroidAutoDetector} */
    public AndroidAutoDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if the parent element is automotiveApp
        if (element.getParentNode() == null ||
                !TAG_AUTOMOTIVE_APP.equals(element.getParentNode().getNodeName())) {
            return;
        }

        // Get the name attribute
        if (!element.hasAttribute(ATTR_NAME)) {
            // Missing name attribute - could report a different issue, but
            // for now we only handle invalid values
            return;
        }

        String nameValue = element.getAttribute(ATTR_NAME);

        // Check if the name value is valid
        boolean isValid = false;
        for (String validName : VALID_USES_NAMES) {
            if (validName.equals(nameValue)) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            Attr nameAttr = element.getAttributeNode(ATTR_NAME);
            context.report(
                    INVALID_USES_TAG_ISSUE,
                    element,
                    nameAttr != null ? context.getLocation(nameAttr) : context.getLocation(element),
                    String.format(
                            "Invalid value for the `name` attribute, valid values are: " +
                            "`media`, `notification`, or `sms`"));
        }
    }
}