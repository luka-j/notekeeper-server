package models;

import com.avaje.ebean.Expr;
import com.avaje.ebean.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.*;

/**
 * Created by luka on 28.12.15..
 */
@Entity
@Table(name = "group_members")
public class GroupMember extends Model {

    public static final int PERM_ROOT = Integer.MAX_VALUE;
    public static final int PERM_REQUEST_WRITE = 60;
    public static final int PERM_INVITED = 300;
    public static final int PERM_WRITE = 1000;
    public static final int PERM_MODIFY = 3000;
    public static final int PERM_OWNER = 4500;
    public static final int PERM_CREATOR = 5000;
    public static final int PERM_READ_DELETED = 1073741824;
    public static final int PERM_READ = 20;

    @Id
    @JsonIgnore
    public long id;

    @ManyToOne
    public User user;
    @ManyToOne
    public Group group;

    public int permission;
    @Lob
    public String filtering = ",";

    public long joinDate = -1;
    public long lastFetchAnnouncements = -1;
    public static final int READ_ANNOUNCEMENTS_PRIOR_TO_JOIN_TIME = 2 * 24 * 60 * 60 * 1000; //2 days

    /**
     * Outside this class, used for temporary "members" - only to satisfy function in {@link controllers.Restrict} if
     * there's no member, i.e. doesn't represent true member that will be saved in the database. Doesn't initialize
     * any other fields except the given ones.
     * @param user User to which this member refers
     * @param group Group to which this member refers
     * @param permission permission this user has in this group
     */
    public GroupMember(User user, Group group, int permission) {
        this.user = user;
        this.group = group;
        this.permission = permission;
    }

    public static Model.Finder<Long, GroupMember> finder = new Model.Finder<>(GroupMember.class);

    public static GroupMember get(long userId, long groupId) {
        if(userId == -1 || groupId == -1) return null;
        Map<String, Object> eqMap = new HashMap<>(2);
        eqMap.put("user_id", userId); eqMap.put("group_id", groupId);
        return finder.where().allEq(eqMap).findUnique();
    }

    @Override
    public int hashCode() {
        return hashCode(user.id, group.id);
    }

    public static int hashCode(long userId, long groupId) {
        int result = (int) (userId ^ (userId >>> 32));
        result = 31 * result + (int) (groupId ^ (groupId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        GroupMember other = (GroupMember)obj;
        return obj != null && other.user.id == this.id && other.group.id == group.id;
    }

    @JsonIgnore
    public Set<Integer> getFilteringYears() {
        if(filtering.equals(",")) return new HashSet<>();
        return stringToSet(filtering);
    }

    public void filter(String filteringStr) {
        filtering = filteringStr; //sanitized string
        /*String sql = "UPDATE group_members SET filtering='" + filtering + "' WHERE id=" + id;

        try (Connection conn = play.db.DB.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }*/
        update();
    }

    public List<Announcement> fetchUnreadAnnouncements() {
        if(permission < PERM_WRITE) return new ArrayList<>();
        List<Announcement> anns = Announcement.getAnnouncements(group, getFilteringYears(), lastFetchAnnouncements);
        lastFetchAnnouncements = System.currentTimeMillis();
        update();
        return anns;
    }

    public static Set<Integer> stringToSet(String str) {
        if(str == null) return new HashSet<>();
        StringTokenizer tokenizer = new StringTokenizer(str, ",", false);
        Set<Integer> set = new HashSet<>((int)(str.length()/2.5));
        while(tokenizer.hasMoreTokens()) {
            String next = tokenizer.nextToken();
            if(next.equals("null"))
                set.add(null);
            else
                set.add(Integer.parseInt(next));
        }
        return set;
    }

    @Override
    public void save() { //masking potential PersistenceExceptions with something harder to detect... sounds about right
        GroupMember existing = finder.where()
                .and(Expr.eq("user_id", user.id), Expr.eq("group_id", group.id))
                .findUnique();
        if(existing == null)
            super.save();
        else {
            existing.filtering = this.filtering;
            existing.joinDate = this.joinDate;
            existing.lastFetchAnnouncements = this.lastFetchAnnouncements;
            existing.permission = this.permission;
            existing.update();
        }
    }

    /**
     * Uses {@link GroupMember#GroupMember(User, Group, int)} to initialize mandatory fields, then sets
     * {@link #lastFetchAnnouncements} and {@link #joinDate} to appropriate times. Requires given User and Group to be
     * in the database before saving this entry, so it doesn't {@link #save()} the model, but expects it to be done
     * externally.
     * @return Newly constructed GroupMember with all fields initialized to appropriate values
     * @see GroupMember#GroupMember(User, Group, int)
     */
    public static GroupMember construct(User user, Group group, int permission) {
        GroupMember existing = finder.where()
                .and(Expr.eq("user_id", user.id), Expr.eq("group_id", group.id))
                .findUnique();
        if(existing != null) return existing;
        GroupMember m = new GroupMember(user, group, permission);
        long time = System.currentTimeMillis();
        m.lastFetchAnnouncements = time-READ_ANNOUNCEMENTS_PRIOR_TO_JOIN_TIME;
        m.joinDate = time;
        return m;
    }
}
