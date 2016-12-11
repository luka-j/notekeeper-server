package models;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import play.data.validation.Constraints;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by luka on 13.12.15..
 */
@Entity
@Table(name = "lessons")
public class Lesson extends Model {
    private static final long ITEM_MASK = 1L << 62; //0100000...00000
    public static Model.Finder<Long, Lesson> finder = new Model.Finder<>(Lesson.class);

    @Id
    public long id;
    public long courseId;
    @Constraints.Required
    @Column(columnDefinition = "VARCHAR(127)")
    public String name;
    public int noteNo;
    public int questionNo;
    @JsonIgnore
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "hidden_lessons")
    public Set<User> hiddenFor = new HashSet<>();
    public int requiredPermission = GroupMember.PERM_READ;

    public static List<Lesson> getByCourse(long courseId, int permission) {
        return finder.where().and(Expr.eq("course_id", courseId), Expr.le("required_permission", permission)).findList();
    }

    public static void delete(long courseId, String name) {
        finder.where().and(Expr.eq("course_id", courseId), Expr.eq("name", name)).findUnique().delete();
    }
    public void delete() {
        hiddenFor.clear();
        super.delete();
        Note.deleteAll(courseId, name);
        Question.deleteAll(courseId, name);
    }

    public static void deleteAll(long courseId) {
        finder.where().eq("courseId", courseId).findEach((Lesson::delete));
    }

    public static void renameLesson(long courseId, String oldName, String newName) {
        System.out.println("Renaming lesson from " + oldName + " to " + newName);
        Lesson l = finder.where().eq("course_id", courseId).eq("name", oldName).findUnique();
        if(l != null) {
            l.name = newName;
            l.update();
            Note.finder.where().eq("lesson", oldName).findEach(bean -> {
                bean.lesson = newName;
                bean.update();
            });
            Question.finder.where().eq("lesson", oldName).findEach(bean -> {
                bean.lesson = newName;
                bean.update();
            });
        }
    }

    public static Lesson get(Long id, int permission) {
        Lesson l= finder.ref(id);
        if(l.requiredPermission > permission)
            return null;
        return l;
    }

    public static Lesson incrementNote(long courseId, String name, int requiredPermission) {
        Lesson lesson = finder.where().eq("course_id", courseId).eq("name", name).findUnique();
        if(lesson==null) {
            lesson = new Lesson();
            lesson.courseId = courseId;
            lesson.name = name;
            lesson.noteNo = 1;
            lesson.questionNo = 0;
            lesson.requiredPermission = requiredPermission;
            lesson.save();
        } else {
            lesson.noteNo++;
            lesson.update();
        }
        return lesson;
    }

    public static Lesson incrementQuestion(long courseId, String name, int requiredPermission) {
        Lesson lesson = finder.where().eq("course_id", courseId).eq("name", name).findUnique();
        if(lesson==null) {
            lesson = new Lesson();
            lesson.courseId = courseId;
            lesson.name = name;
            lesson.noteNo = 0;
            lesson.questionNo = 1;
            lesson.requiredPermission = requiredPermission;
            lesson.save();
        } else {
            lesson.questionNo++;
            lesson.update();
        }
        return lesson;
    }

    public static void decrementNote(long courseId, String name) {
        Lesson l = finder.where().eq("course_id", courseId).eq("name", name).findUnique();
        if(l!=null && l.noteNo>0) {
            l.noteNo--;
            if(!deleteIfEmpty(l)) l.update();
        }
    }

    public static void decrementQuestion(long courseId, String name) {
        Lesson l = finder.where().eq("course_id", courseId).eq("name", name).findUnique();
        if(l!=null && l.questionNo>0) {
            l.questionNo--;
            if(!deleteIfEmpty(l)) l.update();
        }
    }

    /**
     *
     * @param l lesson to be potentially deleted
     * @return true if the item has been deleted, false otherwise
     */
    private static boolean deleteIfEmpty(Lesson l) {
        if(l.noteNo == 0 && l.questionNo == 0) {
            l.delete();
            return true;
        }
        return false;
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

    public String toString() {
        return "course: " + courseId + ", lesson: " + name + ", nNo: " + noteNo + ", qNo: " + questionNo;
    }

    public static void hideFor(long courseId, String lesson, User user) {
        Lesson l = finder.where().and(Expr.eq("courseId", courseId), Expr.eq("name", lesson)).findUnique();
        l.hiddenFor.add(user);
        l.update();
    }
    public void unhideFor(User user) {
        hiddenFor.remove(user);
        update();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Lesson && ((Lesson)obj).id == id;
    }

    @Override
    public int hashCode() {
        return (int)(id ^ (id >>> 32));
    }
}
