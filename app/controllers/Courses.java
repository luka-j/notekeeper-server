package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Course;
import models.GroupMember;
import models.Lesson;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static controllers.Restrict.*;

/**
 * Created by luka on 11.12.15..
 */
public class Courses extends Controller {
    static Form<Course> courseForm = Form.form(Course.class);

    public Result getCoursesInGroup(long groupId) {
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            Set<Integer> filtering = member.getFilteringYears();
            List<Course> courses = Course.getByGroup(groupId, member.permission)
                    .stream()
                    .filter((Course c) -> {
                        if(c.hiddenFor.contains(member.user)) return false;
                        if(filtering.isEmpty()) return true;
                        return filtering.contains(c.year);
                    })
                    .collect(Collectors.toList());
            ObjectNode ret = Json.newObject().put("courseYears", member.group.courseYears)
                    .put("filtering", member.filtering);
            ret.set("courses", Json.toJson(courses));
            //access.log(member, "Courses/all");
            return Results.ok(ret).as("application/json");
        });
    }

    public Result getCourse(long courseId) {
        Course course = Course.get(courseId);
        if(course == null) return notFound("Course doesn't exist");
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            if(course.requiredPermission > member.permission)
                return notFound("Course doesn't exist");
            //access.log(member, "Courses/get, id: " + courseId);
            return ok(Json.toJson(course)).as("application/json");
        });
    }

    public Result updateCourse(long courseId) {
        final Course course = Course.get(courseId);
        Restrict access = MODIFY;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            Form<Course> filledForm = courseForm.bindFromRequest();
            if(filledForm.hasErrors()) return badRequest("Bad request");
            access.log(member, "Courses/edit, id: " + courseId);
            Course.update(course, filledForm.get());
            return ok("Updated");
        });
    }

    public Result addCourse() {
        Form<Course> form = courseForm.bindFromRequest();
        if(courseForm.hasErrors()) return badRequest("Bad request");
        final Course course = form.get();
        Restrict access = WRITE;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            if(!member.filtering.contains("," + course.year + ","))
                member.filter(member.filtering.concat(course.year + ","));
            long newCourseId = Course.create(course);
            access.log(member, "Courses/add, id: " + newCourseId);
            return created(String.valueOf(newCourseId));
        });
    }

    public Result addImage(long id) {
        final Course course = Course.get(id);
        Restrict access = WRITE;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            File img = request().body().asRaw().asFile();
            try {
                Course.updateImage(course, img);
                access.log(member, "Courses/addImage, id: " + id);
                return created("Done");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return internalServerError("500");
            }
        });
    }

    public Result getImage(long id, int size) {
        final Course course = Course.get(id);
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            File img;
            try {
                img = Course.getImage(course, size);
                //access.log(member, "Courses/getImage, id: " + id);
                if(img!=null) {
                    response().setHeader("Content-Disposition", "inline");
                    return ok(img);
                } else
                    return ok();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return internalServerError();
            }
        });
    }

    public Result removeCourse(long id) {
        final Course course = Course.get(id);
        Restrict access = MODIFY;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            course.delete();
            access.log(member, "Courses/remove, id: " + id);
            return ok("Removed");
        });
    }

    public Result removeLesson(long id, String name) {
        final Course course = Course.get(id);
        Restrict access = MODIFY;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            Lesson.delete(id, name);
            access.log(member, "Lessons/remove, course: " + id + ", name: " + name);
            return ok("Removed");
        });
    }

    public Result getLessons(long courseId) {
        final Course course = Course.get(courseId);
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            List<Lesson> lessons = Lesson.getByCourse(courseId, member.permission)
                    .stream()
                    .filter((Lesson l) -> !l.hiddenFor.contains(member.user))
                    .collect(Collectors.toList());
            //access.log(member, "Lessons/all, course: " + courseId);
            return ok(Json.toJson(lessons)).as("application/json");
        });
    }

    public Result renameLesson(long courseId, String oldName) {
        final Course course = Course.get(courseId);
        Restrict access = MODIFY;
        return access.require(ctx(), course.groupId, (GroupMember member) -> {
            String name = DynamicForm.form().bindFromRequest().get("name");
            Lesson.renameLesson(courseId, oldName, name);
            access.log(member, "Lessons/rename, course: " + courseId + ", from: " + oldName + ", to: " + name);
            return ok("Renamed");
        });
    }

    public Result hideCourse(long courseId) {
        Course course = Course.get(courseId);
        return READ_PRESERVE_GROUP.require(ctx(), course.groupId, (GroupMember member) -> {
            course.hideFor(member.user);
            return ok("Hidden");
        });
    }

    public Result showAllCourses(long groupId) {
        return READ_PRESERVE_GROUP.require(ctx(), groupId, (GroupMember member) -> {
            Course.getByGroup(groupId, member.permission).forEach((Course c) -> c.unhideFor(member.user));
            return ok("Shown");
        });
    }

    public Result hideLesson(long courseId, String lesson) {
        return READ_PRESERVE_GROUP.require(ctx(), -1, (GroupMember member) -> {
            Lesson.hideFor(courseId, lesson, member.user);
            return ok("Hidden");
        });
    }

    public Result showAllLessons(long courseId) {
        Course course = Course.get(courseId);
        return READ_PRESERVE_GROUP.require(ctx(), course.groupId, (GroupMember member) -> {
            Lesson.getByCourse(courseId, member.permission).forEach((Lesson l) -> l.unhideFor(member.user));
            return ok("Shown");
        });
    }

    public Result filterCourses(long groupId) {
        return READ_PRESERVE_GROUP.require(ctx(), groupId, (GroupMember member) -> {
            String[] yearsStr = request().body().asFormUrlEncoded().get("years")[0].split(",");
            if(yearsStr.length == 0) return badRequest("Bad request");
            for(String year : yearsStr)
                if(!isInteger(year, 10))
                    return badRequest("Year not a number");
            StringBuilder years = new StringBuilder(yearsStr.length*3);
            years.append(',');
            for(String year : yearsStr)
                years.append(year).append(',');
            member.filter(years.toString());
            return ok("Filtered");
        });
    }

    public static boolean isInteger(String s, int radix) {
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(i == 0 && s.charAt(i) == '-') {
                if(s.length() == 1) return false;
                else continue;
            }
            if(Character.digit(s.charAt(i),radix) < 0) return false;
        }
        return true;
    }
}
