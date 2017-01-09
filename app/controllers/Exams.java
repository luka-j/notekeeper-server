package controllers;

import models.Course;
import models.Exam;
import models.GroupMember;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import java.util.Set;
import java.util.stream.Collectors;

import static controllers.Restrict.*;

/**
 * Created by luka on 11.12.15..
 */
public class Exams extends Controller {
    private static Form<Exam> examForm = Form.form(Exam.class);

    public Result getExamsInGroup(long groupId) {
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), groupId, (GroupMember member) -> {
            Set<Integer> filteringYears = member.getFilteringYears();
            access.log(member, "Exams/all");
            return Results.ok(Json.toJson(Exam.getByGroup(groupId, member.permission)
                        .stream()
                        .filter((Exam e) -> !e.hiddenFor.contains(member.user) && filteringYears.contains(Course.get(e.courseId).year))
                        .collect(Collectors.toList()))).as("application/json");
        });
    }

    public Result getExam(long examId) {
        Exam exam = Exam.get(examId);
        if(exam == null) return notFound("Exam doesn't exist");
        Restrict access = READ_PRESERVE_GROUP;
        return access.require(ctx(), exam.groupId, (GroupMember member) -> {
            if(exam.requiredPermission > member.permission)
                return notFound("Exam doesn't exist");
            access.log(member, "Exams/get, id: " + examId);
            return ok(Json.toJson(exam)).as("application/json");
        });
    }

    public Result updateExam (long examId) {
        Exam exam = Exam.get(examId);
        Restrict access = WRITE;
        return access.require(ctx(), exam.groupId, (GroupMember member) -> {
            Form<Exam> filledForm = examForm.bindFromRequest();
            if(filledForm.hasErrors()) return badRequest("Bad request");
            exam.edit(member.user, filledForm.get());
            access.log(member, "Exams/edit, id: " + examId);
            return ok("Done");
        });
    }

    public Result addExam() {
        Form<Exam> filledForm = examForm.bindFromRequest();
        if(filledForm.hasErrors()) return badRequest("Bad request");
        Exam exam = filledForm.get();
        Restrict access = WRITE;
        return access.require(ctx(), exam.groupId, (GroupMember member) -> {
            long newExam = Exam.create(exam, member.user);
            access.log(member, "Exams/add, id: " + newExam);
            return created(String.valueOf(newExam));
        });
    }

    public Result removeExam(long id) {
        Exam exam = Exam.get(id);
        Restrict access = MODIFY;
        return access.require(ctx(), exam.groupId, (GroupMember member) -> {
            exam.remove(member.user);
            access.log(member, "Exams/remove, id: " + id);
            return ok("Gone");
        });
    }

    public Result getEdits(long id) {
        Exam exam = Exam.get(id);
        Restrict access = WRITE;
        return access.require(ctx(), exam.groupId, (GroupMember member) -> {
            access.log(member, "Exams/history, id: " + id);
            return ok(Json.toJson(exam.editsAsList())).as("application/json");
        });
    }

    public Result hideExam(long examId) {
        Exam exam = Exam.get(examId);
        return READ_PRESERVE_GROUP.require(ctx(), exam.groupId, (GroupMember member) -> {
            exam.hideFor(member.user);
            return ok("Hidden");
        });
    }

    public Result showAllExams(long groupId) {
        return READ_PRESERVE_GROUP.require(ctx(), groupId, (GroupMember member) -> {
            Exam.getByGroup(groupId, member.permission).forEach((Exam e) -> e.unhideFor(member.user));
            return ok("Shown");
        });
    }
}
