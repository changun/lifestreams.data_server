package org.ohmage.lifestreams.utils.data_server;

import org.ohmage.models.OhmageClass;
import org.ohmage.models.OhmageUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by changun on 6/9/14.
 */
public class UserProfile {
    private OhmageUser user;
    public String getUsername() {
        return user.getUsername();
    }

    public String getToken() {
        return user.getToken();
    }




    public List<Map<String, Object>> getAccessibleClasses(){
        List<Map<String, Object>> ret = new ArrayList<>();
        for(OhmageClass cla: user.getPrevilegedClasses()){
            Map<String, Object> classDef = new HashMap<>();
            classDef.put("name", cla.getName());
            classDef.put("users", cla.getUserList(this.getToken()).keySet());
            ret.add(classDef);
        }
        return ret;
    }

    public UserProfile(OhmageUser user) {
        this.user = user;
    }
}
