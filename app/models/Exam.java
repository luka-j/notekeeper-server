package models;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import play.data.validation.Constraints;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luka on 12.12.15..
 */
@Entity
@Table(name = "exams")
public class Exam extends Model implements EditableItem {
    private static final long ITEM_MASK = 5L << 29; //101000...0000

    public static Finder<Long, Exam> finder = new Finder<>(Exam.class);

    @javax.persistence.Id
    public long id;
    @Constraints.Required
    public long groupId;
    @Constraints.Required
    public long courseId;
    @Constraints.Required
    @Constraints.MaxLength(192)
    @Column(columnDefinition = "VARCHAR(192)")
    public String lesson;
    @Constraints.MaxLength(32)
    @Column(columnDefinition = "VARCHAR(32)")
    public String klass;
    @Constraints.MaxLength(64)
    @Column(columnDefinition = "VARCHAR(64)")
    public String type;
    public Long date;
    @JsonIgnore
    public long deletedAt;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "hidden_exams")
    @JsonIgnore
    public Set<User> hiddenFor = new HashSet<>();
    @JsonIgnore
    @Lob
    public String edits = "";
    @JsonIgnore
    public int requiredPermission = GroupMember.PERM_WRITE;

    public String validate() {
        if(id != 0) return "Attempt to set id";
        if(!edits.isEmpty()) return "Attempt to set lastEdits";
        if(!hiddenFor.isEmpty()) return "Attempt to set hiddenFor";
        return null;
    }

    public static List<Exam> getByGroup(long groupId, int permission) {
        return finder.where().and(Expr.eq("group_id", groupId), Expr.le("requiredPermission", permission)).orderBy("klass, date").findList();
    }

    public static Exam get(Long id) {
        return finder.ref(id);
    }

    public static long create(Exam exam, User creator) {
        Edit creation = new Edit(creator, Edit.ACTION_CREATE, System.currentTimeMillis());
        creation.save();
        exam.edits = EditableItem.addEdit(creation.id, exam.edits);
        exam.save();
        return exam.id;
    }

    public static void deleteAll(long courseId) {
        finder.where().eq("courseId", courseId).findEach(Exam::delete);
    }
    public static void removeAll(long courseId, User user) {
        finder.where().eq("courseId", courseId).findEach((e) -> e.remove(user));
    }
    public void remove(User byUser) {
        requiredPermission = GroupMember.PERM_READ_DELETED;
        deletedAt = System.currentTimeMillis();
        Edit deleted = new Edit(byUser, Edit.ACTION_REMOVE, deletedAt);
        deleted.save();
        edits = EditableItem.addEdit(deleted.id, edits);
        update();
    }

    public String toString() {
        return "ID: " + id + "(" + groupId + "), lesson: " + lesson + ", class: " + klass + ", type: " + type;
    }

    @Override
    public void edit(User editor, EditableItem newItem) {
        if(editor == null || newItem == null) return;
        if(!(newItem instanceof Exam)) throw new ClassCastException("Didn't pass exam to Exam#edit");
        Exam newExam = (Exam)newItem;
        long time = System.currentTimeMillis();
        if(newExam.type != null && !newExam.type.isEmpty() && !newExam.type.equals(this.type)) {
            this.type = newExam.type;
            Edit typeEdit = new Edit(editor, Edit.ACTION_CHANGE_TYPE, time);
            typeEdit.save();
            edits = EditableItem.addEdit(typeEdit.id, edits);
        }
        if(newExam.date != null && newExam.date != 0 && !Objects.equals(newExam.date, this.date)) {
            this.date = newExam.date;
            Edit dateEdit = new Edit(editor, Edit.ACTION_CHANGE_DATE, time);
            dateEdit.save();
            edits = EditableItem.addEdit(dateEdit.id, edits);
        }
        if(!newExam.lesson.isEmpty() && !newExam.lesson.equals(this.lesson)) {
            Lesson.renameLesson(courseId, lesson, newExam.lesson);
            this.lesson = newExam.lesson;
            Edit lessonEdit = new Edit(editor, Edit.ACTION_CHANGE_LESSON, time);
            lessonEdit.save();
            edits = EditableItem.addEdit(lessonEdit.id, edits);
        }
        update();
    }

    public List<Edit> editsAsList() {
        if(edits.isEmpty()) return new ArrayList<>(0);
        return Arrays.asList(edits.split(",")).stream().map((String s) -> Edit.get(Long.parseLong(s))).collect(Collectors.toList());
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

    @Override
    public void logImageChanged(User user) {
        throw new UnsupportedOperationException("Exams don't have images!");
    }

    @Override
    public void logImageRemoved(User user) {
        throw new UnsupportedOperationException("Exams don't have images!");
    }

    @Override
    public void logImageAdded(User user) {
        throw new UnsupportedOperationException("Exams don't have images!");
    }

    @Override
    public int hashCode() {
        return (int)(id ^ (id >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Exam && ((Exam)obj).id == id;
    }
}
