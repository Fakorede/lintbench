package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class ArraySizeDetector extends Detector implements XmlScanner {

    private static final String ATTR_NAME = "name";
    private static final String TAG_ITEM = "item";

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("string-array", "integer-array", "array");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = countItems(element);
        File parent = context.file.getParentFile();
        if (parent == null) {
            return;
        }

        String folderName = parent.getName();
        if (folderName.equals("values")) {
            return;
        }

        int baseCount = getBaseArrayCount(context, name, context.file.getName());
        if (baseCount != -1 && baseCount != count) {
            String message = String.format(
                    "Array \"%s\" has %d elements here but %d in values/%s",
                    name, count, baseCount, context.file.getName());
            context.report(ISSUE, context.getLocation(element), message);
        }
    }

    private static int countItems(Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    private static int getBaseArrayCount(XmlContext context, String name, String fileName) {
        File parent = context.file.getParentFile();
        if (parent == null || parent.getParentFile() == null) {
            return -1;
        }

        File baseFolder = new File(parent.getParentFile(), "values");
        File baseFile = new File(baseFolder, fileName);
        if (!baseFile.exists()) {
            return -1;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(baseFile);
            NodeList lists = doc.getElementsByTagName("*");
            for (int i = 0; i < lists.getLength(); i++) {
                Node node = lists.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) node;
                    if (name.equals(el.getAttribute(ATTR_NAME))) {
                        return countItems(el);
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // Ignore parsing errors in base file
        }
        return -1;
    }
}