package dev.njk.index;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** Maps template names, filters, globals and custom tags to the files that reference or define them. */
public final class NjkIndex extends ScalarIndexExtension<String> {
    public static final ID<String, Void> NAME = ID.create("dev.njk.references");

    @Override
    public @NotNull ID<String, Void> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
        return input -> {
            Map<String, Void> result = new HashMap<>();
            for (NjkSourceScanner.Entry entry : NjkSourceScanner.scan(input.getContentAsText(), input.getFile().getExtension())) {
                result.put(entry.key(), null);
            }
            return result;
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public FileBasedIndex.@NotNull InputFilter getInputFilter() {
        return file -> NjkSourceScanner.isIndexed(file.getExtension());
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}
