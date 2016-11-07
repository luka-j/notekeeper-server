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
@Table(name = "questions")
public class Question extends Model implements EditableItem {
    private static final long ITEM_MASK = 3L << 61; //0110000...0000
    public static Finder<Long, Question> finder = new Finder<>(Question.class);

    @javax.persistence.Id
    public long id;
    public long courseId;
    public long groupId;
    @Constraints.Required
    @Column(columnDefinition = "VARCHAR(127)")
    public String lesson;
    @Constraints.Required
    @Column(columnDefinition = "TEXT")
    public String question;
    @Column(columnDefinition = "TEXT")
    public String answer;
    public boolean hasImage = false;
    @JsonIgnore
    public int requiredPermission = GroupMember.PERM_READ;
    @Column(name = "order_col")
    public int order;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "hidden_questions")
    @JsonIgnore
    public Set<User> hiddenFor = new HashSet<>();

    @JsonIgnore
    @Lob
    public String edits = "";

    public String validate() {
        if(id != 0) return "Attempt to set id";
        if(hasImage) return "Attempt to set hasImage";
        if(!edits.isEmpty()) return "Attempt to set lastEdits";
        if(!hiddenFor.isEmpty()) return "Attempt to set hiddenFor";
        return null;
    }

    public static List<Question> getByLesson(long courseId, String lesson, int permission) {
        Map<String, Object> eq = new HashMap<>();
        eq.put("courseId", courseId); eq.put("lesson", lesson); //eq.put("isExam", false);
        return finder.where().allEq(eq).le("requiredPermission", permission).findList();
    }

    public static Question get(Long id) {
        return finder.ref(id);
    }

    public static long create(Question question, User creator) {
        Lesson lesson = Lesson.incrementQuestion(question.courseId, question.lesson, question.requiredPermission);
        if(question.requiredPermission < lesson.requiredPermission)
            question.requiredPermission = lesson.requiredPermission; //maybe a bit ugly, but works!
        question.order = lesson.questionNo;
        Edit creation = new Edit(creator, Edit.ACTION_CREATE, System.currentTimeMillis());
        creation.save();
        question.edits = EditableItem.addEdit(creation.id, question.edits);
        question.save();
        return question.id;
    }

    public void reorder(int newOrder) {
        Connection conn = DB.getConnection();
        try {
            Statement stmt = conn.createStatement();
            if(newOrder < order) {
                stmt.execute("UPDATE questions SET order_col = order_col+1 WHERE " +
                        "course_id=" + courseId +
                        " AND lesson='" + lesson +
                        "' AND order_col BETWEEN " + newOrder + " AND " + order);
            } else if(newOrder > order) {
                stmt.execute("UPDATE questions SET order_col = order_col-1 WHERE " +
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
        Lesson.decrementQuestion(courseId, lesson);
        if(hasImage) {
            try {
                MediaUtils.removeImage("questions/" + id);
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

    public static void updateImage(User user, Question question, File image) throws IOException, InterruptedException {
        if(question.hasImage && image != null) {
            question.logImageChanged(user);
        } else if(question.hasImage) {
            question.logImageRemoved(user);
        } else {
            question.logImageAdded(user);
        }
        question.hasImage = (image != null);
        MediaUtils.updateImage(image, "questions/" + question.id, -1);
        question.update();
    }

    @Nullable
    public static File getImage(Long id, int size) throws IOException, InterruptedException {
        return MediaUtils.getImage("questions/" + id, size);
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
        return "ID: " + id + "(" + groupId + "," + courseId + "), q: " + question + "; a: " + answer;
    }

    @Override
    public void edit(User editor, EditableItem newItem) {
        if(editor == null || newItem == null) return;
        if(!(newItem instanceof Question)) throw new ClassCastException("Didn't pass question to Question#edit");
        Question newQuestion = (Question)newItem;
        this.question = newQuestion.question;
        this.answer = newQuestion.answer;
        long time = System.currentTimeMillis();
        Edit textEdit = new Edit(editor, Edit.ACTION_EDIT_TEXT, time);
        textEdit.save();
        edits = EditableItem.addEdit(textEdit.id, edits);
        if(!newQuestion.lesson.isEmpty() && !newQuestion.lesson.equals(this.lesson)) {
            this.lesson = newQuestion.lesson;
            Edit lessonEdit = new Edit(editor, Edit.ACTION_CHANGE_LESSON, time);
            lessonEdit.save();
            edits = EditableItem.addEdit(textEdit.id, edits);
        }
        update();
    }

    public List<Edit> editsAsList() {
        if(edits.isEmpty()) return new ArrayList<>(0);
        return Arrays.asList(edits.split(",")).stream().map((String s) -> Edit.get(Long.parseLong(s))).collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
        return (int)(id ^ (id >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Question && ((Question)obj).id == id;
    }
}
