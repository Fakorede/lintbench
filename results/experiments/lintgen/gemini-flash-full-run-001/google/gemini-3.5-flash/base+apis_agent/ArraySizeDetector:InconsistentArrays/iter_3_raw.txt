package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ArraySizeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have "
                    + "the same number of elements as the original array. When adding or removing "
                    + "elements to an array, it is easy to forget to update all the locales, and this "
                    + "lint warning finds inconsistencies like these.\n"
                    + "\n"
                    + "Note however that there may be cases where you really want to declare a "
                    + "different number of array items in each configuration (for example where "
                    + "the array represents available options, and those options differ for "
                    + "different layout orientations and so on), so use your own judgment to "
                    + "decide if this is really an error.\n"
                    + "\n"
                    + "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private final Map<String, List<ArrayInfo>> arrayMap = new HashMap<>();
    private final Set<File> visitedFiles = new HashSet<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        List<String> elements = new ArrayList<>();
        elements.add("string-array");
        elements.add("integer-array");
        elements.add("array");
        return elements;
    }

    @Override
    public void beforeCheckProject(Context context) {
        synchronized (arrayMap) {
            arrayMap.clear();
            visitedFiles.clear();
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        visitedFiles.add(context.file);

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        String folderName = context.file.getParentFile().getName();
        Location location = context.getLocation(element);
        ArrayInfo info = new ArrayInfo(folderName, count, location);

        synchronized (arrayMap) {
            List<ArrayInfo> list = arrayMap.get(name);
            if (list == null) {
                list = new ArrayList<>();
                arrayMap.put(name, list);
            }
            list.add(info);
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders != null) {
            for (File res : resourceFolders) {
                File[] subdirs = res.listFiles();
                if (subdirs != null) {
                    for (File subdir : subdirs) {
                        String name = subdir.getName();
                        if (name.equals("values") || name.startsWith("values-")) {
                            File[] files = subdir.listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    if (file.getName().endsWith(".xml") && !visitedFiles.contains(file)) {
                                        parseFile(file, name);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        checkArrays(context);
    }

    private void parseFile(File file, String folderName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);

            NodeList stringArrays = doc.getElementsByTagName("string-array");
            addArrays(stringArrays, folderName, file);

            NodeList integerArrays = doc.getElementsByTagName("integer-array");
            addArrays(integerArrays, folderName, file);

            NodeList arrays = doc.getElementsByTagName("array");
            addArrays(arrays, folderName, file);
        } catch (Exception e) {
            // Ignore parsing errors
        }
    }

    private void addArrays(NodeList list, String folderName, File file) {
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            String name = element.getAttribute("name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            int count = 0;
            NodeList children = element.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                    count++;
                }
            }

            Location location = Location.create(file);
            ArrayInfo info = new ArrayInfo(folderName, count, location);
            synchronized (arrayMap) {
                List<ArrayInfo> infos = arrayMap.get(name);
                if (infos == null) {
                    infos = new ArrayList<>();
                    arrayMap.put(name, infos);
                }
                infos.add(info);
            }
        }
    }

    private void checkArrays(Context context) {
        synchronized (arrayMap) {
            for (Map.Entry<String, List<ArrayInfo>> entry : arrayMap.entrySet()) {
                String arrayName = entry.getKey();
                List<ArrayInfo> infos = entry.getValue();

                ArrayInfo defaultInfo = null;
                for (ArrayInfo info : infos) {
                    if ("values".equals(info.folder)) {
                        defaultInfo = info;
                        break;
                    }
                }

                if (defaultInfo == null) {
                    continue;
                }

                for (ArrayInfo info : infos) {
                    if (info == defaultInfo) {
                        continue;
                    }

                    if (isLocaleFolder(info.folder)) {
                        if (info.count != defaultInfo.count) {
                            String message = String.format(
                                    "Array %1$s has an inconsistent number of items (%2$d in %3$s, but %4$d in %5$s)",
                                    arrayName, info.count, info.folder, defaultInfo.count, defaultInfo.folder
                            );
                            context.report(ISSUE, info.location, message);
                        }
                    }
                }
            }
            arrayMap.clear();
            visitedFiles.clear();
        }
    }

    private static boolean isLocaleFolder(String folderName) {
        if ("values".equals(folderName)) {
            return true;
        }
        if (!folderName.startsWith("values-")) {
            return false;
        }
        String[] segments = folderName.substring("values-".length()).split("-");
        for (String segment : segments) {
            if (segment.startsWith("v") && segment.length() > 1 && Character.isDigit(segment.charAt(1))) {
                continue;
            }
            if (segment.startsWith("mcc") || segment.startsWith("mnc")) {
                continue;
            }
            if (segment.endsWith("dp") || segment.endsWith("dpi")) {
                continue;
            }
            if ("land".equals(segment) || "port".equals(segment) || "night".equals(segment) || "notnight".equals(segment)) {
                continue;
            }
            if (segment.length() == 2 || segment.length() == 3) {
                return true;
            }
            if (segment.startsWith("b+")) {
                return true;
            }
        }
        return false;
    }

    private static class ArrayInfo {
        final String folder;
        final int count;
        final Location location;

        ArrayInfo(String folder, int count, Location location) {
            this.folder = folder;
            this.count = count;
            this.location = location;
        }
    }
}