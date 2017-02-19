package controllers;

import models.Course;
import models.GroupMember;
import models.Note;
import play.data.Form;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

import static controllers.Restrict.MODIFY;
import static controllers.Restrict.WRITE;

/**
 * Created by luka on 11.12.15..
 */
public class Notes extends Controller {
    static Form<Note> noteForm = Form.form(Note.class);

    public Result getNotesInCourse(long courseId, String lesson) {
        long groupId = Course.get(courseId).groupId;
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), groupId, (GroupMember member) ->
                Results.ok(Json.toJson(Note.getByLesson(courseId, lesson, member.permission)
                        .stream()
                        .filter((Note c) -> !c.hiddenFor.contains(member.user))
                        .collect(Collectors.toList()))).as("application/json"));
    }

    public Result getNote(long noteId) {
        Note note = Note.get(noteId);
        if(note == null) return notFound("Note doesn't exist");
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), note.groupId, (GroupMember member) -> {
            if(note.requiredPermission > member.permission)
                return notFound("Note doesn't exist");
            return ok(Json.toJson(note)).as("application/json");
        });
    }

    public Result updateNote(long noteId) {
        Note note = Note.get(noteId);
        if(note == null) return notFound("Note doesn't exist");
        Restrict access = WRITE;
        return access.require(ctx(), note.groupId, (GroupMember member) -> {
            Form<Note> filledForm = noteForm.bindFromRequest();
            if(filledForm.hasErrors()) return badRequest("Bad request");
            note.edit(member.user, filledForm.get());
            access.log(member, "Notes/edit, id: " + noteId);
            return ok("Done");
        });
    }

    public Result addImage(long id) {
        Note note = Note.get(id);
        Restrict access = WRITE;
        return access.require(ctx(), note.groupId, (GroupMember member) -> {
            File img = request().body().asRaw().asFile();
            try {
                Note.updateImage(member.user, note, img);
                access.log(member, "Notes/addImage, id: " + id);
                return created("Done");
            } catch (IOException| InterruptedException e) {
                e.printStackTrace();
                return internalServerError("500");
            }
        });
    }

    public Result addAudio(long id) {
        Note note = Note.get(id);
        Restrict access = WRITE;
        return access.require(ctx(), note.groupId, (GroupMember member) -> {
            File audio = request().body().asRaw().asFile();
            try {
                Note.updateAudio(member.user, note, audio);
                access.log(member, "Notes/addAudio, id: " + id);
                return created("Done");
            } catch (IOException e) {
                e.printStackTrace();
                return internalServerError("500");
            }
        });
    }

    public Result getImage(long id, int size) {
        Note note = Note.get(id);
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), note.groupId, (GroupMember member) -> {
            if(note.requiredPermission > member.permission) return ok();
            try {
                File img = Note.getImage(id, size);
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

    public Result getAudio(long id) {
        Note note = Note.get(id);
        Restrict access = WRITE;
        return access.require(ctx(), note.groupId, (GroupMember member) -> {
            File audio = Note.getAudio(id);
            access.log(member, "Notes/getAudio, id: " + id);
            if(audio!=null) {
                response().setHeader("Content-Disposition", "inline");
                return ok(audio);
            } else
                return ok();
        });
    }

    public Result getEdits(long id) {
        Note note = Note.get(id);
        Restrict access = WRITE;
        return access.require(ctx(), note.groupId, (GroupMember member) -> {
            access.log(member, "Notes/history, id: " + id);
            return ok(Json.toJson(note.editsAsList()));
        });
    }

    public Result addNote() {
        Form<Note> filledForm = noteForm.bindFromRequest();
        if(filledForm.hasErrors()) return badRequest("Bad request");
        Note note = filledForm.get();
        note.groupId = Course.get(note.courseId).groupId;
        Restrict access = WRITE;
        return access.require(ctx(), note.groupId, (GroupMember member) ->  {
            long id = Note.create(note, member.user);
            access.log(member, "Notes/add, id: " + id);
            return created(String.valueOf(id));
        });
    }

    public Result removeNote(long id) {
        Note note = Note.get(id);
        Restrict access = MODIFY;
        return Restrict.MODIFY.require(ctx(), note.groupId, (GroupMember member) -> {
            note.remove(member.user);
            access.log(member, "Notes/remove, id: " + id);
            return ok("Gone");
        });
    }

    public Result hideNote(long noteId) {
        Note note = Note.get(noteId);
        return Restrict.READ_IGNORE_GROUP.require(ctx(), note.groupId, (GroupMember member) -> {
            note.hideFor(member.user);
            return ok("Hidden");
        });
    }

    public Result showAllNotes(long courseId, String lessonName) {
        long groupId = Course.get(courseId).groupId;
        return Restrict.READ_PRESERVE_GROUP.require(ctx(), groupId, (GroupMember member) -> {
            if(lessonName.trim().isEmpty()) return ok();
            Note.getByLesson(courseId,lessonName, member.permission).forEach((Note n) -> n.unhideFor(member.user));
            return ok("Shown");
        });
    }

    public Result reorderNote(long noteId, int newOrder) {
        Note note = Note.get(noteId);
        return WRITE.require(ctx(), note.groupId, (GroupMember member) -> {
           note.reorder(newOrder);
            return ok("Done");
        });
    }
}
