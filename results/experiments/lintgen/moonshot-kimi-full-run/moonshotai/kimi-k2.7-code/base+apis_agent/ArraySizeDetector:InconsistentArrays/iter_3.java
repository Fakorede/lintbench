package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STRING_ARRAY;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

public class ArraySizeDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistent number of elements in arrays",
            "When an array is translated in a different locale, it should normally have "
                    + "the same number of elements as the original array. When adding or removing "
                    + "elements to an array, it is easy to forget to update all the locales, and "
                    + "this lint warning finds inconsistencies like these.\n\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.MESSAGES,
            5,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final DocumentBuilderFactory XML_FACTORY = DocumentBuilderFactory.newInstance();

    static {
        XML_FACTORY.setNamespaceAware(true);
    }

    private Map<String, List<ArrayEntry>> mArrays;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mArrays = new HashMap<>();

        Project project = context.getMainProject();
        if (project == null) {
            return;
        }

        for (File resDir : project.getResourceFolders()) {
            if (resDir == null || !resDir.isDirectory()) {
                continue;
            }
            File[] typeDirs = resDir.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                String folderName = typeDir.getName();
                if (!folderName.startsWith("values")) {
                    continue;
                }
                if (ResourceFolderType.getFolderType(folderName) != ResourceFolderType.VALUES) {
                    continue;
                }
                File[] files = typeDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!file.isFile() || !file.getName().endsWith(".xml")) {
                        continue;
                    }
                    Document doc = parseDocument(file);
                    if (doc == null) {
                        continue;
                    }
                    Element root = doc.getDocumentElement();
                    if (root == null) {
                        continue;
                    }
                    NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node child = children.item(i);
                        if (child.getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        Element element = (Element) child;
                        String tag = element.getTagName();
                        if (!TAG_ARRAY.equals(tag)
                                && !TAG_STRING_ARRAY.equals(tag)
                                && !TAG_INTEGER_ARRAY.equals(tag)) {
                            continue;
                        }
                        String name = element.getAttribute(ATTR_NAME);
                        if (name == null || name.isEmpty()) {
                            continue;
                        }
                        int count = countItems(element);
                        String key = name + "/" + tag;
                        List<ArrayEntry> entries = mArrays.get(key);
                        if (entries == null) {
                            entries = new ArrayList<>();
                            mArrays.put(key, entries);
                        }
                        entries.add(new ArrayEntry(name, tag, folderName, count, null, file));
                    }
                }
            }
        }
    }

    private static Document parseDocument(File file) {
        try {
            return XML_FACTORY.newDocumentBuilder().parse(file);
        } catch (Exception e) {
            return null;
        }
    }

    private static int countItems(Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ARRAY, TAG_STRING_ARRAY, TAG_INTEGER_ARRAY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String tag = element.getTagName();
        int count = countItems(element);
        String folder = context.file.getParentFile().getName();
        String key = name + "/" + tag;

        List<ArrayEntry> entries = mArrays.get(key);
        if (entries == null) {
            entries = new ArrayList<>();
            mArrays.put(key, entries);
        }

        ArrayEntry existing = null;
        for (ArrayEntry entry : entries) {
            if (entry.file.equals(context.file)) {
                existing = entry;
                break;
            }
        }

        Location location = context.getElementLocation(element);
        if (existing != null) {
            existing.count = count;
            existing.location = location;
        } else {
            entries.add(new ArrayEntry(name, tag, folder, count, location, context.file));
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (List<ArrayEntry> entries : mArrays.values()) {
            if (entries.size() <= 1) {
                continue;
            }

            ArrayEntry baseline = null;
            for (ArrayEntry entry : entries) {
                if ("values".equals(entry.folder)) {
                    baseline = entry;
                    break;
                }
            }
            if (baseline == null) {
                baseline = entries.get(0);
            }

            List<ArrayEntry> mismatched = new ArrayList<>();
            for (ArrayEntry entry : entries) {
                if (entry.count != baseline.count) {
                    mismatched.add(entry);
                }
            }
            if (mismatched.isEmpty()) {
                continue;
            }

            boolean reported = false;
            for (ArrayEntry entry : mismatched) {
                if (entry.location != null) {
                    String message = String.format(
                            "Array '%1$s' has %2$d items in %3$s, expected %4$d (%5$s)",
                            entry.name,
                            entry.count,
                            entry.folder,
                            baseline.count,
                            baseline.folder
                    );
                    context.report(ISSUE, entry.location, message);
                    reported = true;
                }
            }

            if (!reported && baseline.location != null) {
                ArrayEntry other = mismatched.get(0);
                String message = String.format(
                        "Array '%1$s' has %2$d items in %3$s, expected %4$d (%5$s)",
                        baseline.name,
                        baseline.count,
                        baseline.folder,
                        other.count,
                        other.folder
                );
                context.report(ISSUE, baseline.location, message);
            }
        }
    }

    private static class ArrayEntry {
        final String name;
        final String tag;
        final String folder;
        final File file;
        int count;
        Location location;

        ArrayEntry(String name, String tag, String folder, int count, Location location, File file) {
            this.name = name;
            this.tag = tag;
            this.folder = folder;
            this.count = count;
            this.location = location;
            this.file = file;
        }
    }
}