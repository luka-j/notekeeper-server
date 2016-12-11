package models;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import play.data.validation.Constraints;
import play.db.DB;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luka on 12.12.15..
 */
@Entity
@Table(name = "notes")
public class Note extends Model implements EditableItem {
    private static final long ITEM_MASK = 3L << 62; //110000...0000

    public static Finder<Long, Note> finder = new Finder<>(Note.class);

    @javax.persistence.Id
    public long id;
    public long courseId;
    //@JsonIgnore
    public long groupId;
    @Constraints.Required
    @Column(columnDefinition = "VARCHAR(127)")
    public String lesson;
    @Constraints.Required
    @Column(columnDefinition = "TEXT")
    public String text;
    public boolean hasImage = false;
    //@JsonIgnore
    public boolean hasAudio = false;
    //@JsonIgnore no jsonIgnore: let app handle missing (hidden) notes if applicable
    @Column(name = "order_col")
    public int order;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "hidden_notes")
    @JsonIgnore
    public Set<User> hiddenFor = new HashSet<>();
    @JsonIgnore
    @Lob
    public String edits = "";
    @JsonIgnore
    public int requiredPermission = GroupMember.PERM_READ;

    public String validate() {
        if(id != 0) return "Attempt to set id";
        if(hasImage || hasAudio) return "Attempt to set hasImage/hasAudio";
        if(!edits.isEmpty()) return "Attempt to set lastEdits";
        if(!hiddenFor.isEmpty()) return "Attempt to set hiddenFor";
        return null;
    }

    public static List<Note> getByLesson(long courseId, String lessonName, int permission) {
        return finder.where()
                .eq("course_id", courseId)
                .eq("lesson", lessonName)
                .le("requiredPermission", permission)
                .orderBy("order ASC")
                .findList();
    }

    public static Note get(Long id) {
        return finder.ref(id);
    }

    public static long create(Note note, User creator) {
        Lesson lesson = Lesson.incrementNote(note.courseId, note.lesson, note.requiredPermission);
        if(note.requiredPermission < lesson.requiredPermission)
            note.requiredPermission = lesson.requiredPermission;
        note.order = lesson.noteNo;
        Edit creation = new Edit(creator, Edit.ACTION_CREATE, System.currentTimeMillis());
        creation.save();
        note.edits = EditableItem.addEdit(creation.id, note.edits);
        note.save();
        return note.id;
    }

    public void reorder(int newOrder) {
        Connection conn = DB.getConnection();
        try {
            Statement stmt = conn.createStatement();
            if(newOrder < order) {
                stmt.execute("UPDATE notes SET order_col = order_col+1 WHERE " +
                        "course_id=" + courseId +
                        " AND lesson='" + lesson +
                        "' AND order_col BETWEEN " + newOrder + " AND " + order);
            } else if(newOrder > order) {
                stmt.execute("UPDATE notes SET order_col = order_col-1 WHERE " +
                        "course_id=" + courseId +
                        " AND lesson='" + lesson +
                        "' AND order_col BETWEEN " + order + " AND " + newOrder);
            }
            stmt.close();
            this.order = newOrder;
            update();
        } catch (SQLException e) {
            throw new RuntimeException("Unexpected SQLException", e);
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new RuntimeException("Unexpected SQLException", e);
            }
        }
    }

    public void delete() {
        hiddenFor.clear();
        super.delete();
        Lesson.decrementNote(courseId, lesson);
        if(hasImage) {
            try {
                MediaUtils.removeImage("notes/" + id);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void deleteAll(long courseId) {
        finder.where().eq("courseId", courseId).findEach(Model::delete);
    }

    public static void deleteAll(long courseId, String lesson) {
        finder.where().and(Expr.eq("courseId", courseId), Expr.eq("lesson", lesson)).findEach(Model::delete);
    }

    public void edit(User editor, EditableItem newItem) {
        if(editor == null || newItem == null) return;
        if(!(newItem instanceof Note)) throw new ClassCastException("Didn't pass note to Note#edit");
        Note newNote = (Note)newItem;
        this.text = newNote.text;
        long time = System.currentTimeMillis();
        Edit textEdit = new Edit(editor, Edit.ACTION_EDIT_TEXT, time);
        textEdit.save();
        edits = EditableItem.addEdit(textEdit.id, edits);
        if(!newNote.lesson.isEmpty() && !newNote.lesson.equals(this.lesson)) {
            Lesson.incrementNote(courseId, newNote.lesson, requiredPermission);
            Lesson.decrementNote(courseId, lesson);
            this.lesson = newNote.lesson;
            Edit lessonEdit = new Edit(editor, Edit.ACTION_CHANGE_LESSON, time);
            lessonEdit.save();
            edits = EditableItem.addEdit(lessonEdit.id, edits);
        }
        update();
    }

    @Override
    public void logImageChanged(User user) {
        Edit edit = new Edit(user, Edit.ACTION_REPLACE_IMAGE, System.currentTimeMillis());
        edit.save();
        edits = EditableItem.addEdit(edit.id, edits);
        update();
    }

    @Override
    public void logImageRemoved(User user) {
        Edit edit = new Edit(user, Edit.ACTION_REMOVE_IMAGE, System.currentTimeMillis());
        edit.save();
        edits = EditableItem.addEdit(edit.id, edits);
        update();
    }

    @Override
    public void logImageAdded(User user) {
        Edit edit = new Edit(user, Edit.ACTION_ADD_IMAGE, System.currentTimeMillis());
        edit.save();
        edits = EditableItem.addEdit(edit.id, edits);
        update();
    }

    public static void updateImage(User user, Note note, File image) throws IOException, InterruptedException {
        if(note.hasImage && image != null) {
            note.logImageChanged(user);
        } else if(note.hasImage) {
            note.logImageRemoved(user);
        } else {
            note.logImageAdded(user);
        }
        note.hasImage = (image != null);
        MediaUtils.updateImage(image, "notes/" + note.id, -1);
        note.update();
    }

    public static void updateAudio(User user, Note note, File audio) throws IOException {
        if(note.hasAudio && audio != null) {
            Edit edit = new Edit(user, Edit.ACTION_REPLACE_AUDIO, System.currentTimeMillis());
            edit.save();
            note.edits = EditableItem.addEdit(edit.id, note.edits);
        } else if(note.hasAudio) {
            Edit edit = new Edit(user, Edit.ACTION_REMOVE_AUDIO, System.currentTimeMillis());
            edit.save();
            note.edits = EditableItem.addEdit(edit.id, note.edits);
        } else {
            Edit edit = new Edit(user, Edit.ACTION_ADD_AUDIO, System.currentTimeMillis());
            edit.save();
            note.edits = EditableItem.addEdit(edit.id, note.edits);
        }
        note.hasAudio = (audio != null);
        note.update();
        MediaUtils.updateAudio(audio, new File(MediaUtils.AUDIO_PATH, "notes/" + note.id));
    }

    public List<Edit> editsAsList() {
        if(edits.isEmpty()) return new ArrayList<>(0);
        return Arrays.asList(edits.split(",")).stream().map((String s) -> Edit.get(Long.parseLong(s))).collect(Collectors.toList());
    }

    @Nullable
    public static File getImage(Long id, int size) throws IOException, InterruptedException {
        return MediaUtils.getImage("notes/" + id, size);
    }

    public static File getAudio(long id) {
        File audio = new File(MediaUtils.AUDIO_PATH, "notes/" + id).getAbsoluteFile();
        if(audio.exists())
            return audio;
        else return null;
    }

    /**
     * Returns id that is unique among all items
     * @param id
     * @return
     * @throws ArithmeticException if id>2^61
     */
    public static long getItemId(long id) {
        if((id & User.RESERVED_BITS) > 0) throw new ArithmeticException("ID too large");
        return id | ITEM_MASK;
    }

    public void hideFor(User user) {
        hiddenFor.add(user);
        update();
    }
    public void unhideFor(User user) {
        hiddenFor.remove(user);
        update();
    }

    public String toString() {
        return "ID: " + id + "(" + groupId + "," + courseId + "), text: " + text;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Note && ((Note)obj).id == id;
    }

    @Override
    public int hashCode() {
        return (int)(id ^ (id >>> 32));
    }
}
