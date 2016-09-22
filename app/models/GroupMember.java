package models;

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
    public static final int PERM_REQUEST_WRITE = 50;
    public static final int PERM_INVITED = 300;
    public static final int PERM_WRITE = 1000;
    public static final int PERM_MODIFY = 3000;
    public static final int PERM_OWNER = 4500;
    public static final int PERM_CREATOR = 5000;
    public static final int PERM_READ_PUBLIC = 0;

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
}
