package controllers;

import models.Course;
import models.GroupMember;
import models.Question;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static controllers.Restrict.MODIFY;
import static controllers.Restrict.WRITE;

/**
 * Created by luka on 11.12.15..
 */
public class Questions extends Controller {
    private static Form<Question> questionForm = Form.form(Question.class);

    public Result getQuestionsInLesson(long courseId, String lesson) {
        long groupId = Course.get(courseId).groupId;
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), groupId, (GroupMember member) ->
                Results.ok(Json.toJson
                        (Question.getByLesson(courseId, lesson, member.permission)
                                .stream()
                                .filter((Question c) -> !c.hiddenFor.contains(member.user))
                                .collect(Collectors.toList())))
                        .as("application/json")
        );
    }

    public Result getQuestion(long questionId) {
        Question question = Question.get(questionId);
        if(question == null) return notFound("Question doesn't exist");
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), question.groupId, (GroupMember member) -> {
            if(question.requiredPermission > member.permission)
                return notFound("Question doesn't exist");
           return ok(Json.toJson(question)).as("application/json");
        });
    }

    public Result updateQuestion (long questionId) {
        Question question = Question.get(questionId);
        Restrict access = WRITE;
        return access.require(ctx(), question.groupId, (GroupMember member) -> {
            Form<Question> filledForm = questionForm.bindFromRequest();
            if(filledForm.hasErrors()) return badRequest("Bad request");
            question.edit(member.user, filledForm.get());
            access.log(member, "Questions/edit, id: " + questionId);
            return ok("Done");
        });
    }

    public Result addQuestion() {
        Form<Question> filledForm = questionForm.bindFromRequest();
        if(filledForm.hasErrors()) return badRequest("Bad request");
        Question question = filledForm.get();
        question.groupId = Course.get(question.courseId).groupId;
        Restrict access = WRITE;
        return access.require(ctx(), question.groupId, (GroupMember member) -> {
            long id = Question.create(question, member.user);
            access.log(member, "Questions/add, id: " + id);
            return created(String.valueOf(id));
        });
    }

    public Result addImage(long id) {
        Question question = Question.get(id);
        Restrict access = WRITE;
        return access.require(ctx(), question.groupId, (GroupMember member) -> {
            File img = request().body().asRaw().asFile();
            try {
                Question.updateImage(member.user, question, img);
                access.log(member, "Questions/addImage, id: " + id);
                return created("Done");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return internalServerError("500");
            }
        });
    }

    public Result getImage(long id, int size) {
        Question question = Question.get(id);
        Function<GroupMember, Result> result = (GroupMember member) -> {
            if(question.requiredPermission > member.permission)
                return ok();
            try {
                File img = Question.getImage(id, size);
                if(img!=null) {
                    response().setHeader("Content-Disposition", "inline");
                    return ok(img);
                } else
                    return ok();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return internalServerError();
            }
        };
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), question.groupId, result);
    }

    public Result removeQuestion(long id) {
        Question question = Question.get(id);
        Restrict access = MODIFY;
        return access.require(ctx(), question.groupId, (GroupMember member) -> {
            question.delete();
            access.log(member, "Questions/remove, id: " + id);
            return ok("Gone");
        });
    }

    public Result getEdits(long id) {
        Question question = Question.get(id);
        Restrict access = WRITE;
        return WRITE.require(ctx(), question.groupId, (GroupMember member) -> {
            access.log(member, "Questions/history, id: " + id);
            return ok(Json.toJson(question.editsAsList())).as("application/json");
        });
    }

    public Result hideQuestion(long questionId) {
        Question question = Question.get(questionId);
        return Restrict.READ.require(ctx(), question.groupId, (GroupMember member) -> {
            question.hideFor(member.user);
            return ok("Hidden");
        });
    }

    public Result showAllQuestions(long courseId, String lesson) {
        long groupId = Course.get(courseId).groupId;
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), groupId, (GroupMember member) -> {
            Question.getByLesson(courseId, lesson, member.permission).forEach((Question q) -> q.unhideFor(member.user));
            return ok("Shown");
        });
    }

    public Result reorderQuestion(long questionId, int newOrder) {
        Question question = Question.get(questionId);
        return WRITE.require(ctx(), question.groupId, (GroupMember member) -> {
            question.reorder(newOrder);
            return ok("Done");
        });
    }
}
