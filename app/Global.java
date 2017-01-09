import akka.actor.ActorSystem;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import models.*;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

/**
 * Created by luka on 8.2.16..
 */
@Singleton
public class Global {
    public static final long EXAM_DEPRECATION_PERIOD = 1000 * 60 * 60 * 24 * 2; //2 days in ms
    public static final long ITEM_REALLY_DELETE_AFTER = 1000L * 60 * 60 * 24 * 7 * 4; //4 weeks in ms


    private static final FiniteDuration hour = FiniteDuration.create(1, TimeUnit.HOURS);
    private static final FiniteDuration day = FiniteDuration.create(1, TimeUnit.DAYS);
    private static final FiniteDuration week = FiniteDuration.create(7, TimeUnit.DAYS);

    private static FiniteDuration getDelayForJob(int index) {
        return FiniteDuration.create(index*10, TimeUnit.MINUTES);
    }


    private static Runnable removeExams = () -> {
        long diff = System.currentTimeMillis() - EXAM_DEPRECATION_PERIOD;
        Exam.finder.where().lt("date", diff).findEach(Exam::delete);
    };
    private static Runnable cleanupGroupMembers = () -> GroupMember.finder.where().le("permission", GroupMember.PERM_READ).findEach(GroupMember::delete);
    private static Runnable resetInviteCounts = () -> User.finder.findEach(User::resetMailCounter);

    ///realDelete jobs (removes from db items marked for deletion)
    private static Runnable cleanupCourses = () -> {
        //Course.finder.where().eq("permission", GroupMember.PERM_READ_DELETED).ge("extract(epoch from now())-deletedAt", 1)
        long deleteFrom = System.currentTimeMillis() - ITEM_REALLY_DELETE_AFTER;
        Course.finder.where().eq("permission", GroupMember.PERM_READ_DELETED).lt("deletedAt", deleteFrom);
    };
    private static Runnable cleanupExams = () -> {
        long deleteFrom = System.currentTimeMillis() - ITEM_REALLY_DELETE_AFTER;
        Exam.finder.where().eq("permission", GroupMember.PERM_READ_DELETED).lt("deletedAt", deleteFrom);
    };
    private static Runnable cleanupLessons = () -> {
        long deleteFrom = System.currentTimeMillis() - ITEM_REALLY_DELETE_AFTER;
        Lesson.finder.where().eq("permission", GroupMember.PERM_READ_DELETED).lt("deletedAt", deleteFrom);
    };
    private static Runnable cleanupNotes = () -> {
        long deleteFrom = System.currentTimeMillis() - ITEM_REALLY_DELETE_AFTER;
        Note.finder.where().eq("permission", GroupMember.PERM_READ_DELETED).lt("deletedAt", deleteFrom);
    };
    private static Runnable cleanupQuestions = () -> {
        long deleteFrom = System.currentTimeMillis() - ITEM_REALLY_DELETE_AFTER;
        Question.finder.where().eq("permission", GroupMember.PERM_READ_DELETED).lt("deletedAt", deleteFrom);
    };



    private static class Job {
        private FiniteDuration delay, frequency;
        private Runnable job;

        private Job(FiniteDuration delay, FiniteDuration frequency, Runnable job) {
            this.delay = delay;
            this.frequency = frequency;
            this.job = job;
        }
    }

    private static final Job[] jobs = new Job[] {
            new Job(getDelayForJob(0), day, removeExams),
            new Job(getDelayForJob(1), day, cleanupGroupMembers),
            new Job(getDelayForJob(2), week, resetInviteCounts),
            new Job(getDelayForJob(3), day, cleanupCourses),
            new Job(getDelayForJob(4), day, cleanupLessons),
            new Job(getDelayForJob(5), day, cleanupExams),
            new Job(getDelayForJob(6), day, cleanupNotes),
            new Job(getDelayForJob(7), day, cleanupQuestions),
    };



    @Inject
    public Global(ActorSystem system) {
        for(Job job : jobs) {
            system.scheduler().schedule(job.delay, job.frequency, job.job, system.dispatcher());
        }
    }
}
