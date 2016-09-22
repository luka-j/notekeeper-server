package models;

/**
 * Created by luka on 28.12.15..
 */
public interface EditableItem {
    int KEEP_EDITS = 16;
    void edit(User user, EditableItem newItem);
    void logImageChanged(User user);
    void logImageRemoved(User user);
    void logImageAdded(User user);

    static String addEdit(long editId, String currentEdits) {
        String[] ids = currentEdits.split(",");
        if(ids.length < KEEP_EDITS) {
            return currentEdits + editId + ",";
        } else {
            Edit.get(Long.parseLong(ids[0])).delete();
            StringBuilder edits = new StringBuilder(128);
            for(int i=1; i<ids.length; i++)
                edits.append(ids[0]).append(',');
            edits.append(editId);
            return edits.toString();
        }
    }
}
