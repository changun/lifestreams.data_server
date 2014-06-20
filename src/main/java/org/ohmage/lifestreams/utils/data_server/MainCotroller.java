package org.ohmage.lifestreams.utils.data_server;

import au.com.bytecode.opencsv.CSVWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.util.Value;
import org.joda.time.DateTime;
import org.mortbay.util.ByteArrayISO8859Writer;
import org.ohmage.lifestreams.models.StreamRecord;
import org.ohmage.lifestreams.stores.IStreamStore;
import org.ohmage.models.OhmageClass;
import org.ohmage.models.OhmageServer;
import org.ohmage.models.OhmageStream;
import org.ohmage.models.OhmageUser;
import org.ohmage.sdk.OhmageStreamIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class MainCotroller {
    final int TOKEN_LIFE_TIME_SECS;
    final OhmageServer ohmageServer;
    final IStreamStore streamStore;
    final JedisPool pool;
    static ObjectMapper mapper = new ObjectMapper();
    static Logger logger = LoggerFactory.getLogger(MainCotroller.class);

    /* Error Status Class */
    @ResponseStatus(value= HttpStatus.BAD_REQUEST, reason="Wrong ohmage credentials!")
    public class WrongOhmageCredential extends RuntimeException {}
    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="You didn't sign in or the session is expired!")
    public class NotSignedInException extends RuntimeException {}


    /* APIs */
    @RequestMapping("/login")
    public
    @ResponseBody
    Map login(@RequestParam(value = "username") String username
                    , @RequestParam(value = "password") String password) {
        OhmageUser user = new OhmageUser(ohmageServer, username, password);
        Jedis jedis = pool.getResource();
        try {
            // check if we can sign in with the username and password
            String token = user.getToken();
            jedis.hset(token, "accessible_users", mapper.writeValueAsString(user.getAccessibleUsers()));
            logger.info("{} sign in. Create token with {} seconds life time",
                    user, TOKEN_LIFE_TIME_SECS);
            jedis.expire(token, TOKEN_LIFE_TIME_SECS);
            pool.returnResource(jedis);
            Map<String, String> ret = new HashMap<>();
            ret.put("token", token);
            return ret;
        } catch (Exception e) {
            pool.returnResource(jedis);
            throw new WrongOhmageCredential();
        }


    }

    @RequestMapping("/profile")
    public
    @ResponseBody
    UserProfile profile(
            @RequestParam(value = "token") String token) throws IOException {
        // return all the accessible users
        Jedis jedis = pool.getResource();
        try {

            if (jedis.exists(token)) {
                pool.returnResource(jedis);
                return new UserProfile(new OhmageUser(ohmageServer, token));
            }
        }catch(Exception ignored){}
        pool.returnResource(jedis);
        throw new NotSignedInException();
    }

    @RequestMapping("/stream")
    public
    @ResponseBody
    List stream(
            @RequestParam(value = "token") String token
            , @RequestParam(value = "username") String username
            , @RequestParam(value = "observerId") String observerId
            , @RequestParam(value = "observerVer") String observerVer
            , @RequestParam(value = "streamId") String streamId
            , @RequestParam(value = "streamVer") String streamVer
            , @RequestParam(value = "start", required = false) String start
            , @RequestParam(value = "end", required = false) String end
            , @RequestParam(value = "max_rows", required = false, defaultValue = "-1") int maxRows
            , @RequestParam(value = "columns", required = false) String columns
            , @RequestParam(value = "chronological", required = false, defaultValue = "true") boolean chronological)
            throws
            IOException {

            Jedis jedis = pool.getResource();
            try {
            // first check if the given token exists
            if (jedis.exists(token)) {
                // if so, check if that token has permission to access the requested user.
                boolean hasAccessTo = mapper.readValue(
                        jedis.hget(token, "accessible_users"),
                        List.class).contains(username);
                if (hasAccessTo) {
                    HashMap<String, Object> ret = new HashMap<>();
                    OhmageUser requestee = new OhmageUser(ohmageServer, username, null);
                    OhmageStream stream = new OhmageStream.Builder()
                            .observerId(observerId)
                            .observerVer(observerVer)
                            .streamId(streamId)
                            .streamVer(streamVer).build();

                    OhmageStreamIterator.SortOrder order = chronological ?
                            OhmageStreamIterator.SortOrder.Chronological :
                            OhmageStreamIterator.SortOrder.ReversedChronological;
                    DateTime startDate = start != null ? new DateTime(start) : null;
                    DateTime endDate = end != null ? new DateTime(end) : null;

                    List<StreamRecord> recs = streamStore.query(stream,
                            requestee,
                            startDate, endDate,
                            order, maxRows);

                    List<ObjectNode> recJsons = new ArrayList<>(recs.size());
                    // covert stream record to json nodes
                    for (StreamRecord rec : recs) {
                        ObjectNode node = rec.toObserverDataPoint();
                        // prune unneeded columns if "columns" parameter is specified
                        if (columns != null) {
                            ObjectNode dataNode = (ObjectNode) node.get("data");
                            String[] requiredCols = columns.split(",");
                            ObjectNode filteredNode = new ObjectNode(mapper.getNodeFactory());
                            for (String requiredCol : requiredCols) {
                                filteredNode.put(requiredCol, dataNode.get(requiredCol));
                            }
                            node.put("data", filteredNode);
                        }
                        recJsons.add(node);
                    }

                    return recJsons;
                } else {

                    throw new WrongOhmageCredential();
                }
            } else {
                throw new NotSignedInException();
            }
        }finally {
            pool.returnResource(jedis);
        }

    }


    @RequestMapping("/export")
    public
    @ResponseBody
    Map export(  HttpServletRequest request ) throws IOException{
        ExportRequest req = mapper.readValue(request.getInputStream(), ExportRequest.class);
        Jedis jedis = pool.getResource();
        try {
            // first check if the given token exists
            if (jedis.exists(req.token)) {
                byte[] bytes = req.exportBytes();
                jedis.hset(req.token.getBytes(), "file".getBytes(), bytes);
                jedis.hset(req.token, "filename", req.getFilename());
                Map<String, String> ret = new HashMap<>();
                ret.put("link", String.format("file?token=%s&filename=%s", req.token, req.getFilename()));
                return ret;
            } else {
                throw new WrongOhmageCredential();
            }
        }finally {
            pool.returnResource(jedis);
        }
    }
    @RequestMapping("/file")
    public
    void file(  @RequestParam(value = "token") String token,
               @RequestParam(value = "filename") String filename,
               HttpServletResponse response) throws IOException{

        Jedis jedis = pool.getResource();
        try {
            // first check if the given token exists
            if (jedis.exists(token)) {
                String storedFile = jedis.hget(token, "filename");
                if (storedFile != null && storedFile.equals(filename)) {
                    byte[] bytes = jedis.hget(token.getBytes(), "file".getBytes());

                    // set content attributes for the response
                    response.setContentType("application/octet-stream");
                    response.setContentLength(bytes.length);
                    // set headers for the response
                    String headerKey = "Content-Disposition";
                    String headerValue = String.format("attachment; filename=\"%s\"",
                            filename);
                    response.setHeader(headerKey, headerValue);
                    // get output stream of the response
                    OutputStream outStream = response.getOutputStream();
                    outStream.write(bytes);
                    return;
                }
            }
            throw new WrongOhmageCredential();
        }finally {
            pool.returnResource(jedis);
        }
    }

    public MainCotroller(OhmageServer server, IStreamStore streamStore, int tokenLifeTimeSecs, int tokenDBIndex){
        this.TOKEN_LIFE_TIME_SECS = tokenLifeTimeSecs;
        this. pool = new JedisPool(new JedisPoolConfig(),
                "localhost", Protocol.DEFAULT_PORT,
                Protocol.DEFAULT_TIMEOUT, null,
                tokenDBIndex
        );
        this.streamStore = streamStore;
        this.ohmageServer = server;
    }
}
