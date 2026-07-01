package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.EnumSet;

public class Utf8Detector extends ResourceXmlDetector {

    private static final String UTF8 = "UTF-8";

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, "
                    + "not all tools handle the XML encoding attribute correctly, and "
                    + "nearly all Android apps use UTF-8, so by using UTF-8 you can protect "
                    + "yourself against subtle bugs when using non-ASCII characters.\n\n"
                    + "In particular, the Android Gradle build system will merge resource XML "
                    + "files assuming the resource files are using UTF-8 encoding.",
            Category.I18N,
            5,
            Severity.ERROR,
            new Implementation(Utf8Detector.class, EnumSet.of(Scope.RESOURCE_FILE_SCOPE)));

    @Override
    protected boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Document document = context.getDocument();
        if (element != document.getDocumentElement()) {
            return;
        }

        String encoding = document.getXmlEncoding();
        if (encoding != null && !encoding.equalsIgnoreCase(UTF8)) {
            String message = String.format(
                    "The resource file is encoded as \"%1$s\", not UTF-8. "
                            + "Please save the file as UTF-8 and use encoding=\"UTF-8\" "
                            + "in the XML declaration.",
                    encoding);
            context.report(ISSUE, context.getLocation(element), message);
        }
    }
}