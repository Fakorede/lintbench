package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends ResourceXmlDetector {

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
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    ArraySizeDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private final Map<String, List<ArrayDeclaration>> mArrays = new HashMap<>();
    private final Set<File> mCheckedFiles = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_STRING_ARRAY,
                SdkConstants.TAG_INTEGER_ARRAY,
                SdkConstants.TAG_ARRAY
        );
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mArrays.clear();
        mCheckedFiles.clear();
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCheckedFiles.add(context.file);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE 
                    && SdkConstants.TAG_ITEM.equals(child.getNodeName())) {
                count++;
            }
        }

        String folderName = context.file.getParentFile().getName();
        Location location = context.getLocation(element);

        ArrayDeclaration decl = new ArrayDeclaration(name, count, folderName, location, context.file);
        List<ArrayDeclaration> list = mArrays.get(name);
        if (list == null) {
            list = new ArrayList<>();
            mArrays.put(name, list);
        }
        list.add(decl);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        boolean isIncremental = context.getProject().getSubset() != null;
        if (isIncremental) {
            List<File> resourceFolders = context.getProject().getResourceFolders();
            for (File res : resourceFolders) {
                File[] subdirs = res.listFiles();
                if (subdirs != null) {
                    for (File subdir : subdirs) {
                        String name = subdir.getName();
                        if (name.startsWith(SdkConstants.FD_RES_VALUES)) {
                            File[] files = subdir.listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    if (file.getPath().endsWith(SdkConstants.DOT_XML) && !mCheckedFiles.contains(file)) {
                                        parseAndRecord(context, file);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (mArrays.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<ArrayDeclaration>> entry : mArrays.entrySet()) {
            String arrayName = entry.getKey();
            List<ArrayDeclaration> declarations = entry.getValue();
            if (declarations.size() <= 1) {
                continue;
            }

            ArrayDeclaration baseline = null;
            for (ArrayDeclaration decl : declarations) {
                if ("values".equals(decl.folder)) {
                    baseline = decl;
                    break;
                }
            }
            if (baseline == null) {
                baseline = declarations.get(0);
            }

            for (ArrayDeclaration decl : declarations) {
                if (decl == baseline) {
                    continue;
                }
                if (decl.size != baseline.size) {
                    ArrayDeclaration primary = decl;
                    ArrayDeclaration secondary = baseline;

                    if (isIncremental) {
                        if (!mCheckedFiles.contains(decl.file) && mCheckedFiles.contains(baseline.file)) {
                            primary = baseline;
                            secondary = decl;
                        } else if (!mCheckedFiles.contains(decl.file) && !mCheckedFiles.contains(baseline.file)) {
                            continue;
                        }
                    }

                    String message = String.format(
                            "Array `%1$s` has an inconsistent number of items (%2$d in %3$s, but %4$d in %5$s)",
                            arrayName, primary.size, primary.folder, secondary.size, secondary.folder
                    );
                    Location location = primary.location;
                    if (secondary.location != null) {
                        location.setSecondary(secondary.location);
                    }
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    private void parseAndRecord(Context context, File file) {
        try {
            CharSequence contents = context.getClient().readFile(file);
            if (contents == null) {
                return;
            }
            Document document = context.getClient().getXmlDocument(file, contents);
            if (document != null && document.getDocumentElement() != null) {
                Element root = document.getDocumentElement();
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        String tagName = child.getNodeName();
                        if (SdkConstants.TAG_STRING_ARRAY.equals(tagName)
                                || SdkConstants.TAG_INTEGER_ARRAY.equals(tagName)
                                || SdkConstants.TAG_ARRAY.equals(tagName)) {
                            Element element = (Element) child;
                            String name = element.getAttribute(SdkConstants.ATTR_NAME);
                            if (name != null && !name.isEmpty()) {
                                int count = 0;
                                NodeList items = element.getChildNodes();
                                for (int j = 0; j < items.getLength(); j++) {
                                    Node item = items.item(j);
                                    if (item.getNodeType() == Node.ELEMENT_NODE
                                            && SdkConstants.TAG_ITEM.equals(item.getNodeName())) {
                                        count++;
                                    }
                                }
                                String folderName = file.getParentFile().getName();
                                Location location = null;
                                try {
                                    location = context.getClient().getXmlParser().getLocation(file, element);
                                } catch (Exception e) {
                                    // ignore
                                }
                                if (location == null) {
                                    location = Location.create(file);
                                }
                                ArrayDeclaration decl = new ArrayDeclaration(name, count, folderName, location, file);
                                List<ArrayDeclaration> list = mArrays.get(name);
                                if (list == null) {
                                    list = new ArrayList<>();
                                    mArrays.put(name, list);
                                }
                                list.add(decl);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors for other files
        }
    }

    private static class ArrayDeclaration {
        final String name;
        final int size;
        final String folder;
        final Location location;
        final File file;

        ArrayDeclaration(String name, int size, String folder, Location location, File file) {
            this.name = name;
            this.size = size;
            this.folder = folder;
            this.location = location;
            this.file = file;
        }
    }
}