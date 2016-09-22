package models;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import play.data.validation.Constraints;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by luka on 12.12.15..
 */
@Entity
@Table(name = "courses")
public class Course extends Model {
    private static final long ITEM_MASK = 1L << 63; //10000...0000

    @javax.persistence.Id
    public long id;
    @Constraints.Required
    public long groupId;
    @Constraints.Required
    @Column(columnDefinition = "VARCHAR(127)")
    public String subject;
    @Column(columnDefinition = "VARCHAR(127)")
    public String teacher;
    public Integer year;
    public boolean hasImage = false;
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "hidden_courses")
    @JsonIgnore
    public Set<User> hiddenFor = new HashSet<>();
    @JsonIgnore
    public int requiredPermission = GroupMember.PERM_READ_PUBLIC;

    public String validate() {
        if(id != 0) return "Attempt to set id";
        if(hasImage) return "Attempt to set hasImage";
        if(!hiddenFor.isEmpty()) return "Attempt to set hiddenFor";
        return null;
    }

    public static List<Course> getByGroup(long parentId, int permission) {
        return finder.where().and(Expr.eq("groupId", parentId), Expr.le("requiredPermission", permission)).findList();
    }

    public static Course get(Long id) {
        return finder.ref(id);
    }

    public static long create(Course course) {
        course.save();
        if(course.year != null) {
            Group group = Group.finder.ref(course.groupId);
            if (!group.courseYears.contains("," + course.year + ",")) {
                group.courseYears = group.courseYears + course.year + ",";
                group.update();
            }
        }
        return course.id;
    }

    public void delete() {
        hiddenFor.clear();
        super.delete();
        if(hasImage) {
            try {
                MediaUtils.removeImage("courses/" + id);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Lesson.deleteAll(id);
        Exam.deleteAll(id);
    }

    public static void update(Course c, Course newCourse) {
        if(newCourse.subject!=null)
            c.subject = newCourse.subject;
        if(newCourse.teacher != null)
            c.teacher = newCourse.teacher;
        if(newCourse.year != null)
            c.year = newCourse.year;
        c.update();
    }

    public static void updateImage(Course c, File image) throws IOException, InterruptedException {
        c.hasImage = (image != null);
        MediaUtils.updateImage(image, "courses/" + c.id, MediaUtils.MAX_THUMBNAIL_SIZE);
        c.update();
    }

    @Nullable
    public static File getImage(Course c, int size) throws IOException, InterruptedException {
        return MediaUtils.getImage("courses/" + c.id, size);
    }

    public void hideFor(User user) {
        hiddenFor.add(user);
        update();
    }
    public void unhideFor(User user) {
        hiddenFor.remove(user);
        update();
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

    public static Finder<Long, Course> finder = new Finder<>(Course.class);

    public String toString() {
        return "ID: " + id + ", name: " + subject + ", class: " + year + "@" + teacher;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Course && ((Course)obj).id == id;
    }

    @Override
    public int hashCode() {
        return (int)(id ^ (id >>> 32));
    }
}
