import akka.actor.ActorSystem;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import models.Exam;
import models.GroupMember;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

/**
 * Created by luka on 8.2.16..
 */
@Singleton
public class Global {
    public static final long EXAM_DEPRECATION_PERIOD = 1000 * 60 * 60 * 24 * 2; //2 days

    @Inject
    public Global(ActorSystem system) {
        FiniteDuration examDelay = FiniteDuration.create(0, TimeUnit.SECONDS);
        FiniteDuration groupMemberDelay = FiniteDuration.create(1, TimeUnit.HOURS);
        FiniteDuration frequency = FiniteDuration.create(1, TimeUnit.DAYS);

        Runnable removeExams = () -> {
            long currTime = System.currentTimeMillis();
            long diff = currTime - EXAM_DEPRECATION_PERIOD;
            Exam.finder.where().lt("date", diff).findEach(Exam::delete);
        };
        Runnable cleanupGroupMembers = () -> GroupMember.finder.where().le("permission", GroupMember.PERM_READ).findEach(GroupMember::delete);

        system.scheduler().schedule(examDelay, frequency, removeExams, system.dispatcher());
        system.scheduler().schedule(groupMemberDelay, frequency, cleanupGroupMembers, system.dispatcher());
    }
}
