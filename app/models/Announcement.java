package models;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.List;
import java.util.Set;

/**
 * Created by luka on 20.10.16..
 */
@Entity
@Table(name = "announcements")
public class Announcement extends Model {
    public static Finder<Long, Announcement> finder = new Finder<>(Announcement.class);

    @Id
    public long id;
    @JsonIgnore
    public long groupId;
    public long date;
    @OneToOne
    public User creator;
    @Lob
    public String text;
    @Lob
    public String years;

    public static final int ALL_GROUPS = -1;

    public String validate() {
        if(id != 0) return "Attempt to set id";
        if(groupId != 0) return "Attempt to set group";
        if(date != 0) return "Attempt to set date";
        if(creator != null) return "Attempt to set creator";
        return null;
    }

    public static long create(Announcement announcement, Group group, User creator) {
        announcement.groupId = group.id;
        announcement.creator = creator;
        announcement.date = System.currentTimeMillis();
        if(announcement.years.contains(" ")) announcement.years = announcement.years.replaceAll(" ", "");
        if(!announcement.years.startsWith(",")) announcement.years=","+announcement.years;
        if(!announcement.years.endsWith(",")) announcement.years+=",";
        announcement.save();
        return announcement.id;
    }

    public static List<Announcement> getAnnouncements(Group group, Set<Integer> years, long since) {
        List<Announcement> anns = finder.where()
                .or(Expr.eq("groupId", group.id), Expr.eq("groupId", ALL_GROUPS))
                .gt("date", since)
                .findList();
        boolean isYearAppropriate;
        for(int i=0; i<anns.size(); i++) {
            isYearAppropriate=false;
            Announcement a = anns.get(i);
            for(Integer year : years) {
                if (a.years.contains("," + year + ",")) {
                    isYearAppropriate = true;
                    break;
                }
            }
            if(!isYearAppropriate) anns.remove(i);
        }
        return anns;
        /*return finder.where()
                .or(Expr.eq("groupId", group.id), Expr.eq("groupId", ALL_GROUPS))
                .gt("date", since)
                .findList()
                .stream()
                .filter((Announcement a) -> {
                    if(a.groupId == ALL_GROUPS) return true;
                    System.out.println(years);
                    for(Integer year : years)
                        if (a.years.contains("," + year + ",")) {
                            return true;
                        }
                    return false;
                }).collect(Collectors.toList());*/
    }
}
