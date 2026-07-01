package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class Utf8Detector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Wrong file encoding",
                    "Encoding used in resource files is not UTF-8\n"
                            + "\n"
                            + "XML supports encoding in a wide variety of character sets. However, "
                            + "not all tools handle the XML encoding attribute correctly, and nearly "
                            + "all Android apps use UTF-8, so by using UTF-8 you can protect yourself "
                            + "against subtle bugs when using non-ASCII characters.\n"
                            + "\n"
                            + "In particular, the Android Gradle build system will merge resource XML "
                            + "files assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    8,
                    Severity.FATAL,
                    new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    public Utf8Detector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Node xmlDeclaration = document.getFirstChild();
        if (xmlDeclaration == null) {
            return;
        }

        // Check if it's a processing instruction (XML declaration)
        if (xmlDeclaration.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE) {
            return;
        }

        String data = xmlDeclaration.getNodeValue();
        if (data == null) {
            return;
        }

        // Look for encoding attribute in the XML declaration
        int encodingIndex = data.indexOf("encoding");
        if (encodingIndex == -1) {
            return;
        }

        // Find the value of the encoding attribute
        int start = data.indexOf('"', encodingIndex);
        int singleStart = data.indexOf('\'', encodingIndex);
        if (start == -1 || (singleStart != -1 && singleStart < start)) {
            start = singleStart;
        }
        if (start == -1) {
            return;
        }

        char quote = data.charAt(start);
        int end = data.indexOf(quote, start + 1);
        if (end == -1) {
            return;
        }

        String encoding = data.substring(start + 1, end);
        if (!encoding.equalsIgnoreCase("utf-8") && !encoding.equalsIgnoreCase("utf8")) {
            context.report(
                    ISSUE,
                    context.getLocation(xmlDeclaration),
                    "Resource files should be saved with UTF-8 encoding; found `" + encoding + "`");
        }
    }
}