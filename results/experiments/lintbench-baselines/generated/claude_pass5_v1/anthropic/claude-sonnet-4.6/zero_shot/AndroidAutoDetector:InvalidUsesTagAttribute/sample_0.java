package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.Collections;

/**
 * Detector for invalid {@code name} attribute values in {@code <uses>} elements
 * within {@code <automotiveApp>} XML files.
 */
public class AndroidAutoDetector extends ResourceXmlDetector {

    /** Valid values for the name attribute of the uses element */
    private static final Collection<String> VALID_USES_NAMES =
            Arrays.asList("media", "notification", "sms");

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTR_NAME = "name";

    public static final Issue INVALID_USES_TAG_ISSUE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid `name` attribute for `uses` element",
            "The `<uses>` element in `<automotiveApp>` should contain a valid value for the" +
            " `name` attribute. Valid values are `media`, `notification`, or `sms`.",
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

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Make sure the parent element is automotiveApp
        if (element.getParentNode() == null ||
                !TAG_AUTOMOTIVE_APP.equals(element.getParentNode().getNodeName())) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            // Missing name attribute
            context.report(
                    INVALID_USES_TAG_ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `name` attribute for `<uses>` element");
            return;
        }

        String nameValue = nameAttr.getValue();
        if (!VALID_USES_NAMES.contains(nameValue)) {
            context.report(
                    INVALID_USES_TAG_ISSUE,
                    nameAttr,
                    context.getLocation(nameAttr),
                    String.format(
                            "Invalid value for `name` attribute in `<uses>` element. " +
                            "Valid values are `media`, `notification`, or `sms`, " +
                            "but was `%1$s`",
                            nameValue));
        }
    }
}