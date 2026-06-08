package adris.altoclef.butler;

import java.util.HashSet;
import java.util.Set;

public class UserListFile {
    public Set<String> users = new HashSet<>();

    public boolean contains(String user) {
        return users.contains(user);
    }
}
