package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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

public class AndroidAutoDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES = "uses";
    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String ATTR_NAME = "name";
    private static final String[] VALID_NAMES = {"media", "notification", "sms"};

    public static final Issue INVALID_USES_TAG_ATTRIBUTE = Issue.create(
            "InvalidUsesTagAttribute",
            "Invalid name attribute for uses element",
            "The <uses> element in <automotiveApp> should contain a valid value for the `name` attribute. "
                    + "Valid values are `media`, `notification`, or `sms`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_AUTOMOTIVE_APP.equals(
                element.getOwnerDocument().getDocumentElement().getTagName())) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            nameAttr = element.getAttributeNode(ATTR_NAME);
        }

        if (nameAttr == null) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    context.getLocation(element),
                    "The `<uses>` element is missing a valid `name` attribute; "
                            + "valid values are `media`, `notification`, or `sms`."
            );
            return;
        }

        String name = nameAttr.getValue();
        if (!isValidName(name)) {
            context.report(
                    INVALID_USES_TAG_ATTRIBUTE,
                    context.getLocation(nameAttr),
                    "Invalid `name` attribute value `" + name + "`; "
                            + "valid values are `media`, `notification`, or `sms`."
            );
        }
    }

    private static boolean isValidName(String name) {
        for (String valid : VALID_NAMES) {
            if (valid.equals(name)) {
                return true;
            }
        }
        return false;
    }
}