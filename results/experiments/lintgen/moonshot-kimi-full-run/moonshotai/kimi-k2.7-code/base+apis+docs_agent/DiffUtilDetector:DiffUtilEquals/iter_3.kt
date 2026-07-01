package test.pkg;
import androidx.recyclerview.widget.DiffUtil;
public class Test {
    public void test() {
        DiffUtil.ItemCallback<String> callback = new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areContentsTheSame(String oldItem, String newItem) {
                return oldItem == newItem;
            }
        };
    }
}