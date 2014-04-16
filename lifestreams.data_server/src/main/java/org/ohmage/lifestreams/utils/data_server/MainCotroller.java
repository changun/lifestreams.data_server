package org.ohmage.lifestreams.utils.data_server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;

import org.joda.time.DateTime;
import org.ohmage.lifestreams.utils.RedisStreamStore;
import org.ohmage.models.OhmageClass;
import org.ohmage.models.OhmageServer;
import org.ohmage.models.OhmageStream;
import org.ohmage.models.OhmageUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import redis.clients.jedis.Jedis;

@RestController
public class MainCotroller {
	@Value("${token.life.time}")
	int TOKEN_LIFE_TIME;
	@Autowired
	RedisStreamStore redisStore;
	@Autowired
	OhmageServer server;
	static ObjectMapper mapper = new ObjectMapper();
	static Logger logger = LoggerFactory.getLogger(MainCotroller.class);

    @RequestMapping("/login")
    public @ResponseBody Map login(@RequestParam(value="username") String username
    					    ,@RequestParam(value="password") String password) {
    	OhmageUser user = new OhmageUser(server, username, password);
    	HashMap<String, Object> ret = new HashMap<String, Object>();
    	try{
    		// check if we can sign in with the username and password
    		String token = user.getToken();
    		
    		Map<OhmageClass, List<String>> userList = user.getAccessibleUsers();
    		// store all the accessible users in redis with the token as the key
    		
    		Jedis jedis = redisStore.getPool().getResource();
        	jedis.select(1);
    		jedis.set(token,  mapper.writeValueAsString(userList));
    		logger.info("{} sign in. Create token with {} seconds life time", user, TOKEN_LIFE_TIME);
    		jedis.expire(token, TOKEN_LIFE_TIME);
    		redisStore.getPool().returnResource(jedis);
    		
    		ret.put("data", userList);
    		ret.put("token", token);
    		ret.put("result", "success");
    	}catch(Exception e){
    		ret.put("result", "fail");
    	}
    	
    	return ret;
    }
    @RequestMapping("/users")
    public @ResponseBody Map<String,Object> users(
    		@RequestParam(value="token") String token) throws JsonParseException, JsonMappingException, IOException {
    	// return all the accessible users
    	HashMap<String, Object> ret = new HashMap<String, Object>();
		Jedis jedis = redisStore.getPool().getResource();
    	jedis.select(1);
    	if(jedis.exists(token)){
			Map<OhmageClass, List<String>> userList = mapper.readValue(jedis.get(token), Map.class);
    		ret.put("data", userList);
    		ret.put("token", token);
    		ret.put("result", "success");
		}
    	else{
    		ret.put("result", "fail");
    	}
		redisStore.getPool().returnResource(jedis);
		return ret;
    }
    @RequestMapping("/stream")
    public @ResponseBody Map<String,Object> stream(
    		@RequestParam(value="token") String token
    	   ,@RequestParam(value="username") String username
    	   ,@RequestParam(value="observerId") String observerId
    	   ,@RequestParam(value="observerVer") String observerVer
    	   ,@RequestParam(value="streamId") String streamId
    	   ,@RequestParam(value="streamVer") String streamVer) throws JsonParseException, JsonMappingException, IOException {
    	
		boolean foundUser = false;
		Jedis jedis = redisStore.getPool().getResource();
    	jedis.select(1);
    	// first check if the given token exists
		if(jedis.exists(token)){
			// if so, check if that token has permission to access the requested user.
			Map<OhmageClass, List<String>> userList = mapper.readValue(jedis.get(token), Map.class);
			for(List<String> users: userList.values()){
				for(String user: users){
					if(user.equals(username)){
						foundUser = true;
					}
				}
			}
		}
		redisStore.getPool().returnResource(jedis);
    	HashMap<String, Object> ret = new HashMap<String, Object>();
		if(foundUser){
			
	    	OhmageUser requestee = new OhmageUser(server , username, null);
	    	OhmageStream stream = new OhmageStream.Builder()
	    								.observerId(observerId)
	    								.observerVer(observerVer)
	    								.streamId(streamId)
	    								.streamVer(streamVer).build();
			List<ObjectNode> json_data = redisStore.query(requestee, stream);
			Collections.sort( json_data, new Comparator< ObjectNode >() {
				@Override
				public int compare(ObjectNode arg0, ObjectNode arg1) {
					DateTime dt0 =  new DateTime(arg0.get("metadata").get("timestamp").asText());
					DateTime dt1 =  new DateTime(arg1.get("metadata").get("timestamp").asText());
					long delta = (dt0.getMillis() - dt1.getMillis());
					if(delta == 0)
						return 0;
					if(delta > 0){
						return 1;
					}
					return -1;
				}} );
			ret.put("result", "success");
			ret.put("data",json_data);

		}else{
			ret.put("result", "fail");
			
		}
		return ret;
    }

	
}
