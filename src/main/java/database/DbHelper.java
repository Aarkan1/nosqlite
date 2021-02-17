package database;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.handlers.WatchData;
import database.handlers.WatchHandler;
import org.sqlite.Function;
import utilities.Utils;

import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

class DbHelper {
    Connection conn;
    private BlockingDeque<Task> tasks = new LinkedBlockingDeque<>();
    private Map<String, List<WatchHandler>> watchers = new HashMap<>();
    private Map<String, Map<String, List<WatchHandler>>> eventWatchers = new HashMap<>();
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * Object to queue in the BlockingDeque
     */
    private class Task<T> {
        String method;
        String query;
        Object[] params;
        Class<T> coll;
        CompletableFuture<String[]> future;

        public Task(String method, String query, Object[] params, Class<T> coll, CompletableFuture<String[]> future) {
            this.method = method;
            this.query = query;
            this.params = params;
            this.coll = coll;
            this.future = future;
        }
    }

    /**
     * Single thread to handle all write operations
     * to prevent concurrency
     *
     * @param conn The database connection
     */
    DbHelper(Connection conn) throws SQLException {
        this.conn = conn;
        boolean useRegex = true;
        if (useRegex) addRegex(conn);

        new Thread(() -> {
            while (isRunning.get() || !tasks.isEmpty()) {
                try {
                    Task task = tasks.take();

                    if (task.method.equals("queryMany")) {
                        String[] future = {"insert", queryMany(task.query, task.params, task.coll)};
                        task.future.complete(future);
                    } else {
                        String[] future = {task.method, query(task.query, task.params, task.coll)};
                        task.future.complete(future);
                    }
                } catch (InterruptedException | SQLException e) {
                    e.printStackTrace();
                }
            }

            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }).start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    void close() {
        isRunning.set(false);
    }

    <T> String run(String method, String query, Object[] params, Class<T> coll) {
        CompletableFuture<String[]> future = new CompletableFuture<>();
        tasks.add(new Task(method, query, params, coll, future));
        try {
            if (!query.startsWith("CREATE")) {
                String[] get = future.get();

                // don't bother converting json if there's no watchers
                if(eventWatchers.get(coll.getSimpleName()) != null || watchers.get(coll.getSimpleName()) != null) {
                    updateWatchers(coll.getSimpleName(), get[0], new WatchData(coll.getSimpleName(), get[0],
                        mapper.readValue("[" + get[1] + "]",
                            mapper.getTypeFactory().constructCollectionType(List.class, coll))));
                }

                return get[1];
            }
        } catch (InterruptedException | ExecutionException | JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    <T> String run(String method, String query, Class<T> coll) {
        return run(method, query, null, coll);
    }

    private <T> String query(String query, Object[] params, Class<T> coll) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(query);
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                Utils.setParams(i + 1, params[i], stmt);
            }
        }

        // fetch doc before delete
        if (query.startsWith("DELETE")) {
            String doc;
            if (params == null) {
                doc = "deleted all";
            } else {
                Object[] id = {params[0]};
                doc = get("SELECT value FROM " + coll.getSimpleName() + " WHERE key = ?", id);
            }
            stmt.executeUpdate();
            return doc;
        }

        stmt.executeUpdate();
        if (params == null) return null;

        if (query.startsWith("INSERT")) {
            Object[] id = {params[0]};
            return get("SELECT value FROM " + coll.getSimpleName() + " WHERE key = ?", id);
        }

        if (query.startsWith("CREATE")) return "created";

        String where = " " + query.substring(query.indexOf("WHERE"));
        Object[] p = new Object[params.length - 2];
        for (int i = 2; i < params.length; i++) {
            p[i - 2] = params[i];
        }

        return get("SELECT value FROM " + coll.getSimpleName() + where, p);
    }

    // get don't require thread safety
    String get(String query) {
        return get(query, null);
    }

    String get(String query, Object[] params) {
        try {
            PreparedStatement stmt = conn.prepareStatement(query);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Utils.setParams(i + 1, params[i], stmt);
                }
            }
            ResultSet rs = stmt.executeQuery();
            return rs.getString(1);
        } catch (SQLException e) {
            // no document found
            if (!e.getMessage().equals("ResultSet closed")) {
                // TODO: Print useful message for bad search query

                e.printStackTrace(); // debug
            }
        }
        return null;
    }

    private <T> String queryMany(String query, Object[] documents, Class<T> coll) {
        List<String> docs = new ArrayList<>();
        try {
            conn.setAutoCommit(false);
            PreparedStatement stmt = conn.prepareStatement(query);

            for (Object model : documents) {
                Map<String, String> field = Utils.getIdField(model);
                String json = mapper.writeValueAsString(model);
                docs.add(json);

                stmt.setString(1, field.get("id"));
                stmt.setString(2, json);
                stmt.executeUpdate();
            }

            conn.commit();
            stmt.close();

            // don't bother converting json if there's no watchers
            if(eventWatchers.get(coll.getSimpleName()) != null || watchers.get(coll.getSimpleName()) != null) {
                updateWatchers(coll.getSimpleName(), "insert", new WatchData(coll.getSimpleName(), "insert",
                        mapper.readValue(docs.toString(), mapper.getTypeFactory().constructCollectionType(List.class, coll))));
            }
            return mapper.writeValueAsString(docs);
        } catch (SQLException | JsonProcessingException e) {
            e.printStackTrace();
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    void watch(String collName, WatchHandler watcher) {
        watchers.putIfAbsent(collName, new ArrayList<>());
        watchers.get(collName).add(watcher);
    }

    void watch(String collName, String event, WatchHandler watcher) {
        eventWatchers.putIfAbsent(collName, new HashMap<>());
        eventWatchers.get(collName).putIfAbsent(event.toLowerCase(), new ArrayList<>());
        eventWatchers.get(collName).get(event.toLowerCase()).add(watcher);
    }

    void updateWatchers(String collName, String event, WatchData watchData) {
        if (eventWatchers.get(collName) != null && eventWatchers.get(collName).get(event) != null) {
            eventWatchers.get(collName).get(event).forEach(w -> w.handle(watchData));
        }
        if(watchers.get(collName) != null) watchers.get(collName).forEach(w -> w.handle(watchData));
    }

    private void addRegex(Connection conn) throws SQLException {
        // Create regexp() function to make the REGEXP operator available
        Function.create(conn, "REGEXP", new Function() {
            @Override
            protected void xFunc() throws SQLException {
                String expression = value_text(0);
                String value = value_text(1);
                if (value == null)
                    value = "";

                Pattern pattern = Pattern.compile(expression);
                result(pattern.matcher(value).find() ? 1 : 0);
            }
        });
    }
}
